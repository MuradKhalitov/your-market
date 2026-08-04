package ru.murad.yourmarket.telegram.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.*;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.payments.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.murad.yourmarket.config.*;
import ru.murad.yourmarket.dto.request.SuccessfulPaymentRequest;
import ru.murad.yourmarket.dto.response.*;
import ru.murad.yourmarket.exception.DraftValidationException;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.service.*;
import ru.murad.yourmarket.telegram.*;
import ru.murad.yourmarket.telegram.keyboard.TelegramKeyboardFactory;
import ru.murad.yourmarket.telegram.keyboard.VehicleKeyboardFactory;
import ru.murad.yourmarket.telegram.keyboard.LocationKeyboardFactory;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.MDC;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateHandler {
    private static final String RULES_TEXT = "Правила:\n• одно объявление — один товар;\n• запрещены незаконные товары и услуги;\n"
            + "• пользователь отвечает за содержание;\n• публикация — после оплаты;\n"
            + "• нарушение правил может привести к удалению.";

    private final TelegramClient client;
    private final TelegramGateway gateway;
    private final TelegramKeyboardFactory keyboards;
    private final TelegramProperties telegram;
    private final PublicationProperties publication;
    private final TelegramUserService userService;
    private final AdvertisementDraftService draftService;
    private final PaymentService paymentService;
    private final AdvertisementPublicationService publicationService;
    private final AdvertisementService advertisementService;
    private final PublicationRetryService retryService;
    private final TelegramChannelLinkService channelLinkService;
    private final ModerationService moderationService;
    private final RateLimitService rateLimitService;
    private final ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository draftPhotoRepository;
    private final StartCommandParser startCommandParser;
    private final TelegramMessageProvider messages;
    private final VehicleDraftFlowService vehicleFlow;
    private final VehicleKeyboardFactory vehicleKeyboards;
    private final VehicleDetailsFormatter vehicleFormatter;
    private final AdvertisementLocationFlowService locationFlow;
    private final LocationKeyboardFactory locationKeyboards;
    private final LocationFormatter locationFormatter;

    public void handle(Update update) {
        if (update == null) {
            log.debug("Пропущен null Telegram update");
            return;
        }
        String correlationId = update.getUpdateId() == null ? UUID.randomUUID().toString() : "tg-" + update.getUpdateId();
        try {
            MDC.put("correlationId", correlationId);
            String eventType = eventType(update);
            log.info("Получен Telegram updateId={}, type={}", update.getUpdateId(), eventType);
            if (!isSupported(update)) {
                log.debug("Пропущен неподдерживаемый Telegram updateId={}, type={}", update.getUpdateId(), eventType);
                return;
            }
            Long userId = extractUserId(update); Long chatId = extractChatId(update);
            if (userId != null) MDC.put("telegramUserId", userId.toString());
            if (chatId != null) MDC.put("chatId", chatId.toString());
            if (shouldRateLimit(update) && !rateLimitService.allow(userId, rateAction(update))) {
                if (chatId != null) gateway.sendText(chatId, "Слишком много действий. Попробуйте через несколько секунд");
                return;
            }
            handleInternal(update);
        } finally { MDC.clear(); }
    }

    private boolean isSupported(Update update) {
        return update.hasMessage() || update.hasCallbackQuery() || update.hasPreCheckoutQuery();
    }

    private String eventType(Update update) {
        if (update.hasMessage()) return "message";
        if (update.hasCallbackQuery()) return "callback_query";
        if (update.hasPreCheckoutQuery()) return "pre_checkout_query";
        if (update.hasChannelPost()) return "channel_post";
        if (update.hasEditedChannelPost()) return "edited_channel_post";
        return "unsupported";
    }

    private void handleInternal(Update update) {
        try {
            if (update.hasPreCheckoutQuery()) handlePreCheckout(update.getPreCheckoutQuery());
            else if (update.hasCallbackQuery()) handleCallback(update.getCallbackQuery());
            else if (update.hasMessage()) handleMessage(update.getMessage());
        } catch (DraftValidationException ex) {
            Long chatId = extractChatId(update);
            if (chatId != null) send(chatId, ex.getMessage(), keyboards.creationNavigation(false));
        } catch (RuntimeException ex) {
            log.error("Ошибка обработки Telegram updateId={}", update.getUpdateId(), ex);
            Long chatId = extractChatId(update);
            if (chatId != null) gateway.sendText(chatId, "Не удалось выполнить действие. Попробуйте ещё раз.");
        }
    }

    private boolean shouldRateLimit(Update update) {
        if (update.hasPreCheckoutQuery()) return false;
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) return false;
        return !(update.hasCallbackQuery() && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().startsWith("mod:"));
    }
    private String rateAction(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().startsWith("retry:")) return "PUBLICATION_RETRY";
        if (update.hasMessage() && TelegramKeyboardFactory.CREATE.equals(update.getMessage().getText())) return "CREATE";
        return update.hasCallbackQuery() ? "CALLBACK" : "MESSAGE";
    }

    private void handleMessage(Message message) {
        User from = message.getFrom();
        Long userId = from.getId();
        Long chatId = message.getChatId();
        userService.registerOrUpdate(userId, chatId, from.getUserName(), from.getFirstName());
        if (message.hasSuccessfulPayment()) { successfulPayment(message); return; }

        String value = message.hasText() ? message.getText().trim() : null;
        StartCommandParser.StartAction startAction = startCommandParser.parse(value);
        if (startAction == StartCommandParser.StartAction.PUBLISH) { startOrResume(chatId, from); return; }
        if (startAction == StartCommandParser.StartAction.MAIN_MENU) { mainMenu(chatId); return; }
        if (isCommand(value, "/menu")) { menuCommand(chatId, userId); return; }
        if (isCommand(value, "/terms")) { send(chatId, messages.terms(publication.getPriceStars()), keyboards.mainMenu()); return; }
        if (isCommand(value, "/paysupport")) { send(chatId, messages.paymentSupport(), keyboards.mainMenu()); return; }
        if (isCommand(value, "/support")) { send(chatId, messages.support(), keyboards.mainMenu()); return; }
        if (TelegramKeyboardFactory.CANCEL_CREATION.equals(value)) { cancelCreation(chatId, userId); return; }
        if (TelegramKeyboardFactory.PHOTOS_DONE.equals(value)) {
            AdvertisementDraft draft = draftService.finishPhotos(userId);
            promptForStep(draft, from.getUserName()); return;
        }
        if (TelegramKeyboardFactory.PHOTOS_CLEAR.equals(value)) {
            draftService.clearPhotos(userId);
            send(chatId, photoPrompt(), keyboards.photoNavigation(false)); return;
        }
        if (TelegramKeyboardFactory.BACK.equals(value)) {
            promptForStep(draftService.moveToPreviousStep(userId), from.getUserName()); return;
        }
        if (TelegramKeyboardFactory.CREATE.equals(value)) { startOrResume(chatId, from); return; }
        if (TelegramKeyboardFactory.MY_ADS.equals(value)) { showMyAds(chatId, userId); return; }
        if (TelegramKeyboardFactory.CHANNEL.equals(value)) {
            send(chatId, "Канал YourMarket: " + telegram.channel().url(), keyboards.mainMenu()); return;
        }
        if (TelegramKeyboardFactory.RULES.equals(value)) {
            send(chatId, RULES_TEXT, keyboards.mainMenu()); return;
        }

        AdvertisementDraft draft = draftService.findActive(userId).orElse(null);
        if (draft == null) {
            send(chatId, "Неизвестная команда. Выберите действие в меню.", keyboards.mainMenu());
            return;
        }
        processDraftInput(message, draft);
    }

    private void processDraftInput(Message message, AdvertisementDraft draft) {
        Long userId = message.getFrom().getId();
        switch (draft.getStep()) {
            case WAITING_FOR_CATEGORY -> {
                sendCategoryPrompt(message.getChatId());
            }
            case WAITING_FOR_VEHICLE_BRAND -> vehicleBrandPrompt(message.getChatId());
            case WAITING_FOR_VEHICLE_BRAND_SEARCH -> vehicleBrandSearch(message.getChatId(), userId, text(message));
            case WAITING_FOR_CUSTOM_VEHICLE_BRAND -> vehicleModelCustomPrompt(message.getChatId(), vehicleFlow.setCustomBrand(userId, text(message)));
            case WAITING_FOR_VEHICLE_MODEL -> vehicleModelPrompt(message.getChatId(), userId);
            case WAITING_FOR_VEHICLE_MODEL_SEARCH -> vehicleModelSearch(message.getChatId(), userId, text(message));
            case WAITING_FOR_CUSTOM_VEHICLE_MODEL -> vehicleYearPrompt(message.getChatId(), vehicleFlow.setCustomModel(userId, text(message)));
            case WAITING_FOR_VEHICLE_YEAR -> vehicleTransmissionPrompt(message.getChatId(), vehicleFlow.setYear(userId, text(message)));
            case WAITING_FOR_VEHICLE_TRANSMISSION -> vehicleTransmissionPrompt(message.getChatId(), draft);
            case WAITING_FOR_VEHICLE_ENGINE_TYPE -> vehicleEnginePrompt(message.getChatId(), draft);
            case WAITING_FOR_VEHICLE_ENGINE_VOLUME -> vehicleMileagePrompt(message.getChatId(), vehicleFlow.setEngineVolume(userId, text(message)));
            case WAITING_FOR_VEHICLE_MILEAGE -> vehicleDrivePrompt(message.getChatId(), vehicleFlow.setMileage(userId, text(message)));
            case WAITING_FOR_TITLE -> {
                continueAfterValue(draftService.setTitle(userId, text(message)), "Шаг 3 из 7 — описание\nВведите описание (10–2000 символов):", message.getFrom());
            }
            case WAITING_FOR_DESCRIPTION -> {
                continueAfterValue(draftService.setDescription(userId, text(message)), "Шаг 4 из 7 — цена\nВведите цену товара:", message.getFrom());
            }
            case WAITING_FOR_PRICE -> {
                continueAfterValue(draftService.setPrice(userId, text(message)), photoPrompt(), message.getFrom());
            }
            case WAITING_FOR_PHOTO -> {
                if (!message.hasPhoto()) throw new DraftValidationException("Нужно отправить одну фотографию товара.");
                String fileId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
                draftService.addPhoto(userId, fileId);
                send(message.getChatId(), "Фотография добавлена. Добавьте ещё или нажмите «✅ Готово».",
                        keyboards.photoNavigation(true));
            }
            case WAITING_FOR_REGION -> locationRegionPrompt(message.getChatId());
            case WAITING_FOR_REGION_SEARCH -> locationRegionSearch(message.getChatId(), text(message));
            case WAITING_FOR_CITY -> locationCityPrompt(message.getChatId(), userId);
            case WAITING_FOR_CITY_SEARCH -> locationCitySearch(message.getChatId(), userId, text(message));
            case WAITING_FOR_CUSTOM_LOCALITY -> { locationFlow.setCustomLocality(userId, text(message)); contactPrompt(message.getChatId(), message.getFrom().getUserName()); }
            case WAITING_FOR_CONTACT_CHOICE -> acceptContact(message, userId);
            case WAITING_FOR_CUSTOM_CONTACT -> preview(draftService.setCustomContact(userId, text(message)));
            case PREVIEW -> send(message.getChatId(), "Используйте кнопку оплаты под предпросмотром или отмените создание.",
                    keyboards.creationNavigation(false));
            default -> promptForStep(draft, message.getFrom().getUserName());
        }
    }

    private void acceptContact(Message message, Long userId) {
        String contact = text(message);
        String username = message.getFrom().getUserName();
        if (username != null && contact.equalsIgnoreCase("@" + username)) {
            preview(draftService.chooseUsernameContact(userId, username));
        } else {
            draftService.requestCustomContact(userId);
            preview(draftService.setCustomContact(userId, contact));
        }
    }

    private void handleCallback(CallbackQuery callback) {
        try { handleCallbackAction(callback); }
        finally { answerCallback(callback.getId()); }
    }

    private void handleCallbackAction(CallbackQuery callback) {
        User from = callback.getFrom();
        Long chatId = callback.getMessage().getChatId();
        userService.registerOrUpdate(from.getId(), chatId, from.getUserName(), from.getFirstName());
        String data = callback.getData();
        if ("cancel".equals(data)) cancelCreation(chatId, from.getId());
        else if (data != null && data.startsWith("ad:category:")) handleCategoryCallback(callback, chatId);
        else if (data != null && data.startsWith("ad:auto:")) handleVehicleCallback(callback, chatId);
        else if (data != null && data.startsWith("ad:loc:")) handleLocationCallback(callback, chatId);
        else if ("edit:menu".equals(data)) send(chatId, "Что изменить?", keyboards.editMenu());
        else if ("edit:preview".equals(data)) draftService.findActive(from.getId()).ifPresent(this::preview);
        else if (data.startsWith("edit:")) {
            AdvertisementCreationStep target = switch (data.substring(5)) {
                case "CATEGORY" -> AdvertisementCreationStep.WAITING_FOR_CATEGORY;
                case "TITLE" -> AdvertisementCreationStep.WAITING_FOR_TITLE;
                case "DESCRIPTION" -> AdvertisementCreationStep.WAITING_FOR_DESCRIPTION;
                case "PRICE" -> AdvertisementCreationStep.WAITING_FOR_PRICE;
                case "PHOTOS" -> AdvertisementCreationStep.WAITING_FOR_PHOTO;
                case "CITY" -> AdvertisementCreationStep.WAITING_FOR_REGION;
                case "CONTACT" -> AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE;
                default -> throw new DraftValidationException("Неизвестное поле редактирования");
            };
            promptForStep(draftService.beginEdit(from.getId(), target), from.getUserName());
        }
        else if ("pay".equals(data)) sendInvoice(chatId, from);
        else if (data.startsWith("delete:")) {
            advertisementService.deletePublished(UUID.fromString(data.substring(7)), from.getId());
            send(chatId, "Объявление снято с публикации.", keyboards.mainMenu());
        } else if (data.startsWith("retry:")) {
            UUID id = UUID.fromString(data.substring(6));
            try {
                AdvertisementResponseDto result = retryService.retryForUser(id, from.getId());
                send(chatId, "✅ Объявление опубликовано",
                        keyboards.openChannel(channelLinkService.messageUrl(result.channelMessageId())));
                mainMenu(chatId);
            } catch (ru.murad.yourmarket.exception.TelegramPublicationException ex) {
                send(chatId, "Не удалось опубликовать объявление. Попробуйте позже", keyboards.mainMenu());
            }
        } else if (data.startsWith("mod:approve:") || data.startsWith("mod:reject:")) {
            UUID id = UUID.fromString(data.substring(data.lastIndexOf(':') + 1));
            AdvertisementResponseDto result;
            if (data.startsWith("mod:approve:")) {
                result = moderationService.approve(id, from.getId());
                gateway.sendText(result.chatId(), "✅ Объявление опубликовано");
            } else {
                result = moderationService.reject(id, from.getId(), "Отклонено модератором");
                gateway.sendText(result.chatId(),
                        "Объявление отклонено модератором. Для возврата оплаты свяжитесь с администратором");
            }
            gateway.deleteMessage(telegram.moderation().chatId(), callback.getMessage().getMessageId());
        }
    }

    private void sendInvoice(Long chatId, User from) {
        PaymentService.InvoiceClaim claim = paymentService.createPaymentAndClaimInvoice(from.getId(), from.getUserName());
        if (!claim.sendAllowed()) {
            String message = switch (claim.result()) {
                case ALREADY_SENT -> "Счёт на " + publication.getPriceStars() + " ⭐ уже был отправлен в этот чат выше. Найдите сообщение с кнопкой оплаты.";
                case IN_PROGRESS -> "Счёт на оплату формируется. Подождите несколько секунд.";
                case UNKNOWN -> "Не удалось определить, был ли счёт отправлен. Новый счёт не создавался. Обратитесь в поддержку.";
                case CLAIMED -> throw new IllegalStateException("Захваченный invoice должен быть отправлен");
            };
            send(chatId, message, keyboards.mainMenu());
            return;
        }
        try {
            gateway.sendInvoice(chatId, claim.payment());
            paymentService.markInvoiceSent(claim.payment().getId(), claim.operationId());
        } catch (ru.murad.yourmarket.exception.TelegramConfirmedFailureException ex) {
            Integer amountStars = null;
            try {
                amountStars = claim.payment().getAmount();
            } catch (IllegalArgumentException amountError) {
                log.warn("Некорректная локальная сумма invoice paymentId={}, advertisementId={}",
                        claim.payment().getId(), claim.payment().getAdvertisementId());
            }
            log.error("Telegram отклонил invoice paymentId={}, advertisementId={}, currency={}, amount={}, minorUnits={}, operationId={}, telegramErrorCode={}, telegramDescription={}",
                    claim.payment().getId(), claim.payment().getAdvertisementId(), claim.payment().getCurrency(),
                    claim.payment().getAmount(), amountStars, claim.operationId(), ex.getErrorCode(),
                    ex.getApiDescription(), ex);
            try {
                paymentService.failInvoiceSending(claim.payment().getId(), claim.operationId(),
                        "Telegram invoice rejected: " + (ex.getApiDescription() == null
                                ? "confirmed failure" : ex.getApiDescription()));
            } catch (RuntimeException releaseError) {
                log.error("Не удалось освободить invoice claim paymentId={}, operationId={}",
                        claim.payment().getId(), claim.operationId(), releaseError);
            }
            if (ex.isCurrencyTotalAmountInvalid()) {
                send(chatId, "Не удалось создать счёт из-за некорректной суммы оплаты. Попробуйте позже или обратитесь к администратору.", keyboards.mainMenu());
                return;
            }
            throw ex;
        } catch (RuntimeException ex) {
            try { paymentService.markInvoiceUnknown(claim.payment().getId(), claim.operationId()); }
            catch (RuntimeException markError) { log.error("Не удалось пометить invoice SEND_UNKNOWN paymentId={}", claim.payment().getId(), markError); }
            throw ex;
        }
    }

    private void startOrResume(Long chatId, User from) {
        AdvertisementDraft draft = draftService.findActive(from.getId()).orElse(null);
        if (draft == null) draft = draftService.startCreation(from.getId(), chatId);
        promptForStep(draft, from.getUserName());
    }

    private void promptForStep(AdvertisementDraft draft, String username) {
        switch (draft.getStep()) {
            case WAITING_FOR_CATEGORY -> sendCategoryPrompt(draft.getChatId());
            case WAITING_FOR_VEHICLE_BRAND -> vehicleBrandPrompt(draft.getChatId());
            case WAITING_FOR_VEHICLE_BRAND_SEARCH -> send(draft.getChatId(), "Введите название или несколько букв марки автомобиля:", keyboards.creationNavigation(false));
            case WAITING_FOR_CUSTOM_VEHICLE_BRAND -> send(draft.getChatId(), "Введите марку автомобиля:", keyboards.creationNavigation(false));
            case WAITING_FOR_VEHICLE_MODEL -> vehicleModelPrompt(draft.getChatId(), draft.getTelegramUserId());
            case WAITING_FOR_VEHICLE_MODEL_SEARCH -> send(draft.getChatId(), "Введите название или несколько букв модели:", keyboards.creationNavigation(false));
            case WAITING_FOR_CUSTOM_VEHICLE_MODEL -> send(draft.getChatId(), "Введите модель автомобиля:", keyboards.creationNavigation(false));
            case WAITING_FOR_VEHICLE_YEAR -> vehicleYearPrompt(draft.getChatId(), draft);
            case WAITING_FOR_VEHICLE_TRANSMISSION -> vehicleTransmissionPrompt(draft.getChatId(), draft);
            case WAITING_FOR_VEHICLE_ENGINE_TYPE -> vehicleEnginePrompt(draft.getChatId(), draft);
            case WAITING_FOR_VEHICLE_ENGINE_VOLUME -> send(draft.getChatId(), "Введите объём двигателя в литрах, например 2.0:", keyboards.creationNavigation(false));
            case WAITING_FOR_VEHICLE_MILEAGE -> vehicleMileagePrompt(draft.getChatId(), draft);
            case WAITING_FOR_VEHICLE_DRIVE_TYPE -> vehicleDrivePrompt(draft.getChatId(), draft);
            case WAITING_FOR_TITLE -> creationPrompt(draft.getChatId(), "Шаг 2 из 7 — название\nВведите название (3–150 символов):");
            case WAITING_FOR_DESCRIPTION -> creationPrompt(draft.getChatId(), "Шаг 3 из 7 — описание\nВведите описание (10–2000 символов):");
            case WAITING_FOR_PRICE -> creationPrompt(draft.getChatId(), "Шаг 4 из 7 — цена\nВведите цену товара:");
            case WAITING_FOR_PHOTO -> send(draft.getChatId(), photoPrompt(),
                    keyboards.photoNavigation(draft.getTelegramFileId() != null));
            case WAITING_FOR_REGION -> locationRegionPrompt(draft.getChatId());
            case WAITING_FOR_REGION_SEARCH -> send(draft.getChatId(), "Введите название или несколько букв региона:", keyboards.creationNavigation(false));
            case WAITING_FOR_CITY -> locationCityPrompt(draft.getChatId(), draft.getTelegramUserId());
            case WAITING_FOR_CITY_SEARCH -> send(draft.getChatId(), "Введите название или несколько букв города:", keyboards.creationNavigation(false));
            case WAITING_FOR_CUSTOM_LOCALITY -> send(draft.getChatId(), "Введите название населённого пункта, например: село Хучни, посёлок Мамедкала или аул:", keyboards.creationNavigation(false));
            case WAITING_FOR_CONTACT_CHOICE -> contactPrompt(draft.getChatId(), username);
            case WAITING_FOR_CUSTOM_CONTACT -> creationPrompt(draft.getChatId(), "Шаг 7 из 7 — контакт\nВведите контакт для связи:");
            case PREVIEW -> preview(draft);
            default -> sendCategoryPrompt(draft.getChatId());
        }
    }

    private String categoryPrompt() {
        return "Шаг 1 из 7 — категория\nВыберите категорию объявления:";
    }

    private void sendCategoryPrompt(Long chatId) {
        send(chatId, "Создание объявления: выберите категорию кнопкой ниже.", keyboards.creationNavigation(true));
        sendInline(chatId, categoryPrompt(), keyboards.categorySelection());
    }

    private void handleCategoryCallback(CallbackQuery callback, Long chatId) {
        Long userId = callback.getFrom().getId();
        AdvertisementDraft draft = draftService.findActive(userId).orElse(null);
        if (draft == null || draft.getStep() != AdvertisementCreationStep.WAITING_FOR_CATEGORY) {
            send(chatId, "Этот шаг уже завершён. Продолжите заполнение объявления ниже.",
                    keyboards.creationNavigation(false));
            return;
        }
        String code = callback.getData().substring("ad:category:".length());
        AdvertisementCategory category = AdvertisementCategory.fromCode(code).orElse(null);
        if (category == null) {
            send(chatId, "Неизвестная категория. Выберите вариант с помощью кнопок ниже.",
                    keyboards.creationNavigation(true));
            sendInline(chatId, categoryPrompt(), keyboards.categorySelection());
            return;
        }
        AdvertisementDraft updated = draftService.setCategory(userId, category);
        if (updated.getStep() == AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND) {
            send(chatId, "Категория: " + category.displayLabel() + "\n\nТеперь выберите марку автомобиля.",
                    keyboards.creationNavigation(false));
            vehicleBrandPrompt(chatId);
            return;
        }
        send(chatId, "Категория: " + category.displayLabel()
                + "\n\nШаг 2 из 7 — название\nТеперь отправьте название объявления.",
                keyboards.creationNavigation(false));
        log.info("Selected advertisement category telegramUserId={}, category={}", userId, category.name());
    }
    private void handleVehicleCallback(CallbackQuery callback, Long chatId) {
        Long userId = callback.getFrom().getId();
        String data = callback.getData();
        draftService.findActive(userId)
                .filter(draft -> Objects.equals(draft.getChatId(), chatId) && draft.getCategory() == AdvertisementCategory.AUTO)
                .orElseThrow(() -> new DraftValidationException("Этот выбор автомобиля не принадлежит активному черновику."));
        if ("ad:auto:page".equals(data)) return;
        if (data.startsWith("ad:auto:bp:")) { vehicleBrandPrompt(chatId, page(data, "ad:auto:bp:")); return; }
        if (data.startsWith("ad:auto:mp:")) { vehicleModelPrompt(chatId, userId, page(data, "ad:auto:mp:")); return; }
        if ("ad:auto:bs".equals(data)) { vehicleFlow.beginBrandSearch(userId); send(chatId, "Введите название или несколько букв марки автомобиля:", keyboards.creationNavigation(false)); return; }
        if ("ad:auto:ms".equals(data)) { vehicleFlow.beginModelSearch(userId); send(chatId, "Введите название или несколько букв модели:", keyboards.creationNavigation(false)); return; }
        if ("ad:auto:brands".equals(data)) { vehicleFlow.backToBrands(userId); vehicleBrandPrompt(chatId); return; }
        if ("ad:auto:models".equals(data)) { vehicleModelPrompt(chatId, userId); return; }
        if (data.startsWith("ad:auto:sb:")) { vehicleModelPrompt(chatId, userId, vehicleFlow.setBrandSearchResult(userId, data.substring(11))); return; }
        if (data.startsWith("ad:auto:sm:")) { vehicleYearPrompt(chatId, vehicleFlow.chooseModel(userId, data.substring(11))); return; }
        if (data.startsWith("ad:auto:b:")) { AdvertisementDraft draft = vehicleFlow.chooseBrand(userId, data.substring(10)); if (draft.getStep() == AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_BRAND) send(chatId, "Введите марку автомобиля:", keyboards.creationNavigation(false)); else vehicleModelPrompt(chatId, userId, draft); return; }
        if (data.startsWith("ad:auto:m:")) { AdvertisementDraft draft = vehicleFlow.chooseModel(userId, data.substring(10)); if (draft.getStep() == AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_MODEL) send(chatId, "Введите модель автомобиля:", keyboards.creationNavigation(false)); else vehicleYearPrompt(chatId, draft); return; }
        if (data.startsWith("ad:auto:t:")) { vehicleEnginePrompt(chatId, vehicleFlow.setTransmission(userId, enumValue(TransmissionType.class, data.substring(10)))); return; }
        if (data.startsWith("ad:auto:e:")) { AdvertisementDraft draft = vehicleFlow.setEngineType(userId, enumValue(EngineType.class, data.substring(10))); if (draft.getStep() == AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE) vehicleMileagePrompt(chatId, draft); else send(chatId, "Введите объём двигателя в литрах, например 2.0:", keyboards.creationNavigation(false)); return; }
        if (data.startsWith("ad:auto:d:")) { vehicleFlow.setDriveType(userId, enumValue(DriveType.class, data.substring(10))); send(chatId, "Характеристики автомобиля сохранены. Теперь отправьте название объявления.", keyboards.creationNavigation(false)); return; }
        throw new DraftValidationException("Неизвестное действие выбора автомобиля.");
    }

    private void handleLocationCallback(CallbackQuery callback, Long chatId) {
        Long userId = callback.getFrom().getId(); String data = callback.getData();
        AdvertisementDraft draft = draftService.findActive(userId).filter(d -> Objects.equals(d.getChatId(), chatId))
                .orElseThrow(() -> new DraftValidationException("Этот выбор местоположения не принадлежит активному черновику."));
        if ("ad:loc:page".equals(data)) return;
        if (data.startsWith("ad:loc:rp:")) { locationRegionPrompt(chatId, page(data,"ad:loc:rp:")); return; }
        if (data.startsWith("ad:loc:cp:")) { locationCityPrompt(chatId,userId,page(data,"ad:loc:cp:")); return; }
        if ("ad:loc:rs".equals(data)) { locationFlow.beginRegionSearch(userId); send(chatId,"Введите название или несколько букв региона:",keyboards.creationNavigation(false)); return; }
        if ("ad:loc:cs".equals(data)) { locationFlow.beginCitySearch(userId); send(chatId,"Введите название или несколько букв города:",keyboards.creationNavigation(false)); return; }
        if ("ad:loc:other".equals(data)) { locationFlow.beginCustomLocality(userId); send(chatId,"Введите название населённого пункта, например: село Хучни, посёлок Мамедкала или аул:",keyboards.creationNavigation(false)); return; }
        if ("ad:loc:regions".equals(data)) { locationFlow.backToRegions(userId); locationRegionPrompt(chatId); return; }
        if ("ad:loc:cities".equals(data)) { locationCityPrompt(chatId,userId); return; }
        if (data.startsWith("ad:loc:r:")) { AdvertisementDraft result=locationFlow.chooseRegion(userId,data.substring(9)); if(result.getStep()==AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE) contactPrompt(chatId,callback.getFrom().getUserName()); else locationCityPrompt(chatId,userId); return; }
        if (data.startsWith("ad:loc:c:")) { locationFlow.chooseCity(userId,data.substring(9)); contactPrompt(chatId,callback.getFrom().getUserName()); return; }
        throw new DraftValidationException("Неизвестное действие местоположения.");
    }
    private void locationRegionPrompt(Long chatId){locationRegionPrompt(chatId,0);} private void locationRegionPrompt(Long chatId,int page){sendInline(chatId,"Выберите регион:",locationKeyboards.regions(page));}
    private void locationCityPrompt(Long chatId,Long userId){locationCityPrompt(chatId,userId,0);} private void locationCityPrompt(Long chatId,Long userId,int page){AdvertisementDraft d=draftService.findActive(userId).orElseThrow(()->new DraftValidationException("Активный черновик не найден."));sendInline(chatId,"Выберите город в регионе «"+d.getRegionNameSnapshot()+"»:",locationKeyboards.cities(d.getRegionCode(),page));}
    private void locationRegionSearch(Long chatId,String q){if(q.trim().length()<2||q.trim().length()>60)throw new DraftValidationException("Введите от 2 до 60 символов для поиска региона.");var markup=locationKeyboards.regionSearch(q);sendInline(chatId,"Выберите регион из результатов поиска:",markup);}
    private void locationCitySearch(Long chatId,Long userId,String q){AdvertisementDraft d=draftService.findActive(userId).orElseThrow(()->new DraftValidationException("Активный черновик не найден."));if(q.trim().length()<2||q.trim().length()>60)throw new DraftValidationException("Введите от 2 до 60 символов для поиска города.");sendInline(chatId,"Выберите город из результатов поиска:",locationKeyboards.citySearch(d.getRegionCode(),q));}

    private <T extends Enum<T>> T enumValue(Class<T> type, String code) { try { return Enum.valueOf(type, code); } catch (IllegalArgumentException ex) { throw new DraftValidationException("Неизвестное значение. Выберите вариант кнопкой."); } }
    private int page(String data, String prefix) { try { return Math.max(0, Integer.parseInt(data.substring(prefix.length()))); } catch (RuntimeException ex) { throw new DraftValidationException("Некорректная страница каталога."); } }
    private void vehicleBrandPrompt(Long chatId) { vehicleBrandPrompt(chatId, 0); }
    private void vehicleBrandPrompt(Long chatId, int page) { sendInline(chatId, "Выберите марку автомобиля:", vehicleKeyboards.brands(page)); }
    private void vehicleModelPrompt(Long chatId, Long userId) { vehicleModelPrompt(chatId, userId, 0); }
    private void vehicleModelPrompt(Long chatId, Long userId, AdvertisementDraft ignored) { vehicleModelPrompt(chatId, userId, 0); }
    private void vehicleModelPrompt(Long chatId, Long userId, int page) {
        String brand = vehicleFlow.find(userId).map(VehicleDraftDetails::getBrandCode)
                .orElseThrow(() -> new DraftValidationException("Сначала выберите марку автомобиля."));
        if ("OTHER".equals(brand)) send(chatId, "Введите модель автомобиля:", keyboards.creationNavigation(false));
        else sendInline(chatId, "Выберите модель автомобиля:", vehicleKeyboards.models(brand, page));
    }
    private void vehicleBrandSearch(Long chatId, Long userId, String query) { if (query.trim().length() < 2 || query.trim().length() > 40) throw new DraftValidationException("Введите от 2 до 40 символов для поиска марки."); sendInline(chatId, "Выберите марку из результатов поиска:", vehicleKeyboards.brandSearch(query)); }
    private void vehicleModelSearch(Long chatId, Long userId, String query) { if (query.trim().length() < 2 || query.trim().length() > 40) throw new DraftValidationException("Введите от 2 до 40 символов для поиска модели."); String brand = vehicleFlow.find(userId).map(VehicleDraftDetails::getBrandCode).orElseThrow(() -> new DraftValidationException("Сначала выберите марку автомобиля.")); sendInline(chatId, "Выберите модель из результатов поиска:", vehicleKeyboards.modelSearch(brand, query)); }
    private void vehicleYearPrompt(Long chatId, AdvertisementDraft ignored) { send(chatId, "Введите год выпуска автомобиля, например 2018:", keyboards.creationNavigation(false)); }
    private void vehicleModelCustomPrompt(Long chatId, AdvertisementDraft ignored) { send(chatId, "Введите модель автомобиля:", keyboards.creationNavigation(false)); }
    private void vehicleTransmissionPrompt(Long chatId, AdvertisementDraft ignored) { sendInline(chatId, "Выберите коробку передач:", vehicleKeyboards.transmission()); }
    private void vehicleEnginePrompt(Long chatId, AdvertisementDraft ignored) { sendInline(chatId, "Выберите тип двигателя:", vehicleKeyboards.engine()); }
    private void vehicleMileagePrompt(Long chatId, AdvertisementDraft ignored) { send(chatId, "Введите пробег в километрах, например 85000:", keyboards.creationNavigation(false)); }
    private void vehicleDrivePrompt(Long chatId, AdvertisementDraft ignored) { sendInline(chatId, "Выберите привод:", vehicleKeyboards.drive()); }

    private String photoPrompt() {
        return "Шаг 5 из 7 — фотографии\nОтправьте от 1 до 5 фотографий.\nПосле загрузки нажмите “✅ Готово”.";
    }

    private void contactPrompt(Long chatId, String username) {
        String suggestion = username == null || username.isBlank() ? "" : " Можно отправить @" + username + ".";
        creationPrompt(chatId, "Шаг 7 из 7 — контакт\nВведите контакт для связи." + suggestion);
    }


    private void menuCommand(Long chatId, Long userId) {
        if (draftService.findActive(userId).isPresent()) {
            send(chatId, "У вас есть незавершённое объявление. Нажмите “Разместить объявление”, чтобы продолжить, или отмените его",
                    keyboards.mainMenu());
        } else mainMenu(chatId);
    }

    private void cancelCreation(Long chatId, Long userId) {
        draftService.cancel(userId);
        send(chatId, "Создание объявления отменено", keyboards.mainMenu());
    }

    private void handlePreCheckout(PreCheckoutQuery query) {
        PreCheckoutResult result = paymentService.approvePreCheckout(query.getFrom().getId(), query.getInvoicePayload(),
                query.getCurrency(), query.getTotalAmount().longValue());
        try {
            client.execute(AnswerPreCheckoutQuery.builder().preCheckoutQueryId(query.getId())
                    .ok(result.approved()).errorMessage(result.errorMessage()).build());
        } catch (Exception ex) { throw new IllegalStateException("Не удалось ответить на pre-checkout", ex); }
    }

    private void successfulPayment(Message message) {
        SuccessfulPayment value = message.getSuccessfulPayment();
        PaymentService.SuccessfulPaymentResult result = paymentService.processSuccessfulPayment(
                new SuccessfulPaymentRequest(message.getFrom().getId(), value.getInvoicePayload(), value.getCurrency(),
                        value.getTotalAmount().longValue(), value.getTelegramPaymentChargeId()));
        AdvertisementResponseDto current = advertisementService.findById(result.advertisementId());
        if (publication.isModerationEnabled()) {
            if (current.status() == AdvertisementStatus.WAITING_FOR_MODERATION)
                moderationService.submit(result.advertisementId());
            send(message.getChatId(), "Оплата " + publication.getPriceStars()
                    + " ⭐ получена. Объявление отправлено на модерацию.", keyboards.mainMenu());
            return;
        }
        if (current.status() == AdvertisementStatus.PUBLISHED || current.status() == AdvertisementStatus.REJECTED
                || current.status() == AdvertisementStatus.DELETED || current.status() == AdvertisementStatus.EXPIRED) {
            mainMenu(message.getChatId()); return;
        }
        try {
            if (current.status() == AdvertisementStatus.PAID || current.status() == AdvertisementStatus.PUBLICATION_FAILED)
                publicationService.publish(result.advertisementId());
            current = advertisementService.findById(result.advertisementId());
            switch (current.status()) {
                case PUBLISHED -> send(message.getChatId(), "✅ Объявление опубликовано: " + telegram.channel().url(), keyboards.mainMenu());
                case PUBLICATION_IN_PROGRESS -> send(message.getChatId(), "Оплата подтверждена. Публикация выполняется", keyboards.mainMenu());
                case PUBLICATION_RECONCILIATION_REQUIRED -> send(message.getChatId(), "Оплата подтверждена, но состояние публикации требует проверки администратором", keyboards.mainMenu());
                case REJECTED -> send(message.getChatId(), "Объявление отклонено", keyboards.mainMenu());
                case DELETED -> send(message.getChatId(), "Объявление удалено", keyboards.mainMenu());
                case EXPIRED -> send(message.getChatId(), "Срок публикации объявления истёк", keyboards.mainMenu());
                default -> send(message.getChatId(), "Оплата подтверждена. Публикация ожидает обработки", keyboards.mainMenu());
            }
        } catch (RuntimeException ex) {
            send(message.getChatId(), "Оплата принята, но публикация временно не выполнена. Мы повторим её позже.", keyboards.mainMenu());
        }
    }


    private void preview(AdvertisementDraft d) {
        String caption = "%s <b>%s</b>\n\n💰 Цена: %s ₽\n📍 Город: %s\nКатегория: %s\n\n%s\n\n👤 Продавец: %s\n\nСтоимость публикации: %s ⭐"
                .formatted(d.getCategory().getEmoji(), TelegramGatewayImpl.html(d.getTitle()),
                        TelegramGatewayImpl.price(d.getItemPrice()), TelegramGatewayImpl.html(locationFormatter.format(d)),
                        d.getCategory().getDisplayName(), TelegramGatewayImpl.html(d.getDescription()),
                        TelegramGatewayImpl.html(d.getContact()), publication.getPriceStars());
        if (d.getCategory() == AdvertisementCategory.AUTO) {
            String vehicle = vehicleFlow.find(d.getTelegramUserId()).map(vehicleFormatter::format).orElse("");
            if (!vehicle.isBlank()) caption += "\n\n" + vehicle;
        }
        try {
            var photos = draftPhotoRepository.findByDraftIdOrderByPosition(d.getId());
            if (photos.size() > 1) {
                var media = new ArrayList<org.telegram.telegrambots.meta.api.objects.media.InputMedia>();
                for (int i = 0; i < photos.size(); i++) {
                    var builder = InputMediaPhoto.builder().media(photos.get(i).getTelegramFileId());
                    if (i == 0) builder.caption(caption).parseMode("HTML");
                    media.add(builder.build());
                }
                client.execute(SendMediaGroup.builder().chatId(d.getChatId()).medias(media).build());
                send(d.getChatId(), "Выберите действие с объявлением:", keyboards.preview());
                return;
            }
            String fileId = photos.isEmpty() ? d.getTelegramFileId() : photos.get(0).getTelegramFileId();
            client.execute(SendPhoto.builder().chatId(d.getChatId()).photo(new InputFile(fileId))
                    .caption(caption).parseMode("HTML").replyMarkup(keyboards.preview()).build());
        } catch (Exception ex) { throw new IllegalStateException("Не удалось отправить предпросмотр", ex); }
    }

    private void showMyAds(Long chatId, Long userId) {
        List<AdvertisementResponseDto> ads = advertisementService.findRecentForUser(userId);
        if (ads.isEmpty()) send(chatId, "У вас пока нет объявлений.", keyboards.mainMenu());
        else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Moscow"));
            ads.forEach(ad -> send(chatId, "%s\n%s ₽\nСтатус: %s\nСоздано: %s".formatted(ad.title(),
                    TelegramGatewayImpl.price(ad.itemPrice()), ad.status(), formatter.format(ad.createdAt())),
                    ad.status() == AdvertisementStatus.PUBLISHED
                            ? keyboards.publishedActions(ad.id(), channelLinkService.messageUrl(ad.channelMessageId()))
                            : ad.status() == AdvertisementStatus.PUBLICATION_FAILED ? keyboards.retryPublication(ad.id()) : null));
            send(chatId, "Показаны последние объявления.", keyboards.mainMenu());
        }
    }

    private void mainMenu(Long chatId) {
        send(chatId, "Добро пожаловать в YourMarket!\n\nЗдесь вы можете разместить объявление\nв нашем Telegram-канале.\n\nСтоимость публикации: "
                + publication.getPriceStars() + " ⭐", keyboards.mainMenu());
    }

    private void creationPrompt(Long chatId, String text) {
        send(chatId, text, keyboards.creationNavigation(text.startsWith("Шаг 1")));
    }
    private void continueAfterValue(AdvertisementDraft draft, String nextPrompt, User user) {
        if (draft.getStep() == AdvertisementCreationStep.PREVIEW) preview(draft);
        else if (draft.getStep() == AdvertisementCreationStep.WAITING_FOR_PHOTO)
            send(draft.getChatId(), nextPrompt, keyboards.photoNavigation(draft.getTelegramFileId() != null));
        else creationPrompt(draft.getChatId(), nextPrompt);
    }
    private boolean isCommand(String value, String command) {
        return value != null && command.equals(value.split("\\s+")[0]);
    }
    private String text(Message message) {
        if (!message.hasText()) throw new DraftValidationException("Отправьте текстовое значение.");
        return message.getText();
    }
    private void send(Long chatId, String text, ReplyKeyboard keyboard) {
        try {
            SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder().chatId(chatId).text(text);
            if (keyboard != null) builder.replyMarkup(keyboard);
            client.execute(builder.build());
        } catch (Exception ex) { throw new IllegalStateException("Не удалось отправить сообщение", ex); }
    }
    private void sendInline(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось отправить сообщение", ex);
        }
    }
    private void answerCallback(String id) {
        try { client.execute(AnswerCallbackQuery.builder().callbackQueryId(id).build()); }
        catch (Exception ex) { log.warn("Не удалось ответить callback query", ex); }
    }
    private Long extractChatId(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }
    private Long extractUserId(Update update) {
        if (update.hasMessage() && update.getMessage().getFrom() != null) return update.getMessage().getFrom().getId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) return update.getCallbackQuery().getFrom().getId();
        if (update.hasPreCheckoutQuery()) return update.getPreCheckoutQuery().getFrom().getId();
        return null;
    }
}
