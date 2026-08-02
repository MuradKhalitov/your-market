package ru.murad.yourmarket.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.payments.RefundStarPayment;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.murad.yourmarket.config.*;
import ru.murad.yourmarket.exception.TelegramPublicationException;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.telegram.keyboard.TelegramKeyboardFactory;
import java.text.*;
import java.util.List;
import java.util.regex.Pattern;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.service.CurrencyAmountConverter;

@Component
@RequiredArgsConstructor
public class TelegramGatewayImpl implements TelegramGateway {
    private static final Pattern USERNAME = Pattern.compile("^@[A-Za-z0-9_]{5,32}$");
    private final TelegramClient client;
    private final TelegramProperties telegram;
    private final PublicationProperties publication;
    private final TelegramKeyboardFactory keyboards;
    private final AdvertisementPhotoRepository photoRepository;
    private final CurrencyAmountConverter currencyAmountConverter;

    @Override
    public Integer publishAdvertisement(Advertisement ad) {
        return publishAdvertisementMessages(ad).getFirst();
    }

    @Override
    public List<Integer> publishAdvertisementMessages(Advertisement ad) {
        List<Integer> ids = new java.util.ArrayList<>(publishAdvertisementPrimaryMessages(ad));
        if (needsSeparateContactMessage(ad)) ids.add(publishAdvertisementContactMessage(ad));
        return ids;
    }

    @Override
    public List<Integer> publishAdvertisementPrimaryMessages(Advertisement ad) {
        try {
            var photos = photoRepository.findByAdvertisementIdOrderByPosition(ad.getId());
            if (photos.size() > 1) {
                var media = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.media.InputMedia>();
                for (int i = 0; i < photos.size(); i++) {
                    var builder = InputMediaPhoto.builder().media(photos.get(i).getTelegramFileId());
                    if (i == 0) builder.caption(channelCaption(ad)).parseMode("HTML");
                    media.add(builder.build());
                }
                List<Message> messages = client.execute(SendMediaGroup.builder().chatId(telegram.channel().id()).medias(media).build());
                return messages.stream().map(Message::getMessageId).toList();
            }
            String fileId = photos.isEmpty() ? ad.getTelegramFileId() : photos.getFirst().getTelegramFileId();
            SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                    .chatId(telegram.channel().id())
                    .photo(new InputFile(fileId))
                    .caption(channelCaption(ad)).parseMode("HTML");
            if (USERNAME.matcher(ad.getContact()).matches()) builder.replyMarkup(keyboards.seller(ad.getContact()));
            Message message = client.execute(builder.build());
            return List.of(message.getMessageId());
        } catch (Exception ex) {
            throw new TelegramPublicationException("Не удалось опубликовать объявление", ex);
        }
    }

    @Override
    public void deleteChannelMessage(Integer messageId) {
        try {
            client.execute(DeleteMessage.builder().chatId(telegram.channel().id()).messageId(messageId).build());
        } catch (Exception ex) {
            if (ex instanceof org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException request
                    && Integer.valueOf(400).equals(request.getErrorCode()) && request.getApiResponse() != null
                    && request.getApiResponse().toLowerCase(java.util.Locale.ROOT).contains("message to delete not found"))
                throw new ru.murad.yourmarket.exception.TelegramMessageAlreadyAbsentException("Telegram message already absent", ex);
            throw new ru.murad.yourmarket.exception.TelegramMessageDeletionException("Telegram message deletion failed", ex);
        }
    }

    @Override
    public void sendInvoice(Long chatId, Payment payment) {
        if (!"XTR".equals(payment.getCurrency())) {
            throw new IllegalArgumentException("Only XTR invoices are supported for publication");
        }
        int minorUnits;
        try {
            minorUnits = currencyAmountConverter.toMinorUnits(payment.getAmount(), payment.getCurrency());
        } catch (IllegalArgumentException exception) {
            throw new ru.murad.yourmarket.exception.TelegramConfirmedFailureException(
                    "Invalid invoice amount", null, "CURRENCY_TOTAL_AMOUNT_INVALID", exception);
        }
        try {
            SendInvoice.SendInvoiceBuilder<?, ?> builder = SendInvoice.builder().chatId(chatId)
                    .title("Публикация объявления")
                    .description("Размещение объявления в канале YourMarket")
                    .payload(payment.getPayload()).currency("XTR")
                    .prices(List.of(new LabeledPrice("Размещение объявления",
                            minorUnits)))
                    .needName(false).needPhoneNumber(false).needEmail(false)
                    .needShippingAddress(false).isFlexible(false);
            client.execute(builder.build());
        } catch (Exception ex) {
            if (ex instanceof org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException request)
                throw new ru.murad.yourmarket.exception.TelegramConfirmedFailureException(
                        "Telegram rejected invoice", request.getErrorCode(), request.getApiResponse(), request);
            throw new TelegramPublicationException("Ambiguous invoice result", ex);
        }
    }

    @Override
    public void refundStarPayment(Long userId, String telegramPaymentChargeId) {
        try {
            client.execute(RefundStarPayment.builder().userId(userId)
                    .telegramPaymentChargeId(telegramPaymentChargeId).build());
        } catch (Exception exception) {
            throw new TelegramPublicationException("Не удалось выполнить возврат Telegram Stars", exception);
        }
    }

    @Override
    public void sendText(Long chatId, String text) {
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception ex) {
            throw new TelegramPublicationException("Не удалось отправить сообщение", ex);
        }
    }

    @Override
    public Integer sendModerationRequest(Advertisement ad) {
        List<Integer> media=sendModerationMedia(ad);
        return sendModerationAction(ad);
    }

    @Override public List<Integer> sendModerationMedia(Advertisement ad) {
        if (telegram.moderation().chatId() == null || telegram.moderation().chatId().isBlank())
            throw new TelegramPublicationException("Не настроен чат модерации", null);
        try {
            String caption = channelCaption(ad) + "\n\nAdvertisement ID: <code>" + ad.getId()
                    + "</code>\nTelegram user ID: <code>" + ad.getTelegramUserId()
                    + "</code>\nОплачено: " + ad.getPaidAt();
            var photos = photoRepository.findByAdvertisementIdOrderByPosition(ad.getId());
            if (photos.size() > 1) {
                var media = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.media.InputMedia>();
                for (int i = 0; i < photos.size(); i++) {
                    var builder = InputMediaPhoto.builder().media(photos.get(i).getTelegramFileId());
                    if (i == 0) builder.caption(caption).parseMode("HTML");
                    media.add(builder.build());
                }
                return client.execute(SendMediaGroup.builder().chatId(telegram.moderation().chatId()).medias(media).build()).stream().map(Message::getMessageId).toList();
            }
            Message message = client.execute(SendPhoto.builder().chatId(telegram.moderation().chatId())
                    .photo(new InputFile(ad.getTelegramFileId())).caption(caption).parseMode("HTML")
                    .build());
            return List.of(message.getMessageId());
        } catch (Exception ex) { throw new TelegramPublicationException("Не удалось отправить на модерацию", ex); }
    }

    @Override public Integer sendModerationAction(Advertisement ad){try{return client.execute(SendMessage.builder().chatId(telegram.moderation().chatId()).text("Решение по объявлению "+ad.getId()).replyMarkup(keyboards.moderation(ad.getId())).build()).getMessageId();}catch(Exception ex){throw new TelegramPublicationException("Не удалось отправить действия модерации",ex);}}

    @Override
    public void deleteMessage(String chatId, Integer messageId) {
        try { client.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build()); }
        catch (Exception ex) { throw new TelegramPublicationException("Не удалось удалить сообщение", ex); }
    }

    private String channelCaption(Advertisement ad) {
        return "%s <b>%s</b>\n\n💰 Цена: %s ₽\n📍 %s\n%s\n\n%s\n\n👤 Продавец: %s\n\n#%s #%s".formatted(
                ad.getCategory().getEmoji(), html(ad.getTitle()), price(ad.getItemPrice()),
                html(ad.getCity()), html(ad.getCategory().getDisplayName()), html(ad.getDescription()),
                contact(ad.getContact()), ad.getCategory().getHashtag(), hashtag(ad.getCity()));
    }
    private String contact(String value) {
        if (!USERNAME.matcher(value).matches()) return html(value);
        String username = value.substring(1);
        return "<a href=\"https://t.me/" + username + "\">@" + username + "</a>";
    }
    public static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    public static String price(java.math.BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(new java.util.Locale("ru")));
        return format.format(value);
    }
    private String hashtag(String value) {
        String normalized = value.trim().replaceAll("[^\\p{L}\\p{N}_]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "город" : normalized;
    }
}
