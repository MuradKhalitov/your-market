package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.exception.*;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.service.AdvertisementDraftService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AdvertisementDraftServiceImpl implements AdvertisementDraftService {
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999.99");
    private final AdvertisementDraftRepository repository;
    private final ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository photoRepository;
    private final ru.murad.yourmarket.repository.AdvertisementRepository advertisementRepository;
    private final ru.murad.yourmarket.repository.PaymentRepository paymentRepository;
    private final ru.murad.yourmarket.repository.VehicleDraftDetailsRepository vehicleDetailsRepository;

    @Override
    public AdvertisementDraft startCreation(Long userId, Long chatId) {
        AdvertisementDraft draft = repository.findByTelegramUserId(userId)
                .orElseGet(() -> AdvertisementDraft.builder().telegramUserId(userId).chatId(chatId).build());
        draft.setChatId(chatId);
        if (draft.getId() != null) { photoRepository.deleteByDraftId(draft.getId()); vehicleDetailsRepository.deleteByAdvertisementDraftId(draft.getId()); }
        draft.setStep(AdvertisementCreationStep.WAITING_FOR_CATEGORY);
        draft.setCategory(null); draft.setTitle(null); draft.setDescription(null); draft.setItemPrice(null);
        draft.setTelegramFileId(null); draft.setCity(null); draft.setContact(null);
        draft.setEditMode(false);
        AdvertisementDraft saved = repository.save(draft);
        log.info("Начато создание объявления, step={}", saved.getStep());
        return saved;
    }

    @Override @Transactional(readOnly = true)
    public Optional<AdvertisementDraft> findActive(Long userId) { return repository.findByTelegramUserId(userId); }

    @Override
    public AdvertisementDraft setCategory(Long userId, AdvertisementCategory value) {
        AdvertisementCreationStep next = value == AdvertisementCategory.AUTO
                ? AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND : AdvertisementCreationStep.WAITING_FOR_TITLE;
        return update(userId, AdvertisementCreationStep.WAITING_FOR_CATEGORY,
                d -> d.setCategory(value), next);
    }

    @Override
    public AdvertisementDraft setTitle(Long userId, String value) {
        String text = normalized(value, 3, 150, "Название должно содержать от 3 до 150 символов.");
        return update(userId, AdvertisementCreationStep.WAITING_FOR_TITLE,
                d -> d.setTitle(text), AdvertisementCreationStep.WAITING_FOR_DESCRIPTION);
    }

    @Override
    public AdvertisementDraft setDescription(Long userId, String value) {
        String text = normalized(value, 10, 2000, "Описание должно содержать от 10 до 2000 символов.");
        return update(userId, AdvertisementCreationStep.WAITING_FOR_DESCRIPTION,
                d -> d.setDescription(text), AdvertisementCreationStep.WAITING_FOR_PRICE);
    }

    @Override
    public AdvertisementDraft setPrice(Long userId, String value) {
        BigDecimal price;
        try {
            price = new BigDecimal(value.trim().replace(',', '.')).stripTrailingZeros();
        } catch (RuntimeException ex) {
            throw new DraftValidationException("Введите цену числом, например 70000 или 70000.50.");
        }
        if (price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2) {
            throw new DraftValidationException("Цена должна быть больше 0, не выше 999999999.99 и содержать до 2 знаков после запятой.");
        }
        BigDecimal accepted = price;
        return update(userId, AdvertisementCreationStep.WAITING_FOR_PRICE,
                d -> d.setItemPrice(accepted), AdvertisementCreationStep.WAITING_FOR_PHOTO);
    }

    @Override
    public AdvertisementDraft setPhoto(Long userId, String fileId) {
        if (fileId == null || fileId.isBlank()) throw new DraftValidationException("Отправьте одну фотографию товара.");
        return update(userId, AdvertisementCreationStep.WAITING_FOR_PHOTO,
                d -> d.setTelegramFileId(fileId), AdvertisementCreationStep.WAITING_FOR_CITY);
    }

    @Override
    public AdvertisementDraft addPhoto(Long userId, String fileId) {
        if (fileId == null || fileId.isBlank()) throw new DraftValidationException("Отправьте фотографию товара.");
        AdvertisementDraft draft = requiredAt(userId, AdvertisementCreationStep.WAITING_FOR_PHOTO);
        long count = photoRepository.countByDraftId(draft.getId());
        if (count >= 5) throw new DraftValidationException("Можно загрузить не более 5 фотографий. Нажмите «✅ Готово».");
        photoRepository.save(ru.murad.yourmarket.model.AdvertisementDraftPhoto.builder().draftId(draft.getId())
                .telegramFileId(fileId).position((int) count).build());
        if (draft.getTelegramFileId() == null) draft.setTelegramFileId(fileId);
        return repository.save(draft);
    }

    @Override
    public AdvertisementDraft finishPhotos(Long userId) {
        AdvertisementDraft draft = requiredAt(userId, AdvertisementCreationStep.WAITING_FOR_PHOTO);
        if (photoRepository.countByDraftId(draft.getId()) < 1 && draft.getTelegramFileId() == null)
            throw new DraftValidationException("Добавьте минимум одну фотографию.");
        if (draft.isEditMode()) { draft.setEditMode(false); draft.setStep(AdvertisementCreationStep.PREVIEW); }
        else draft.setStep(AdvertisementCreationStep.WAITING_FOR_CITY);
        return repository.save(draft);
    }

    @Override
    public AdvertisementDraft clearPhotos(Long userId) {
        AdvertisementDraft draft = requiredAt(userId, AdvertisementCreationStep.WAITING_FOR_PHOTO);
        photoRepository.deleteByDraftId(draft.getId());
        draft.setTelegramFileId(null);
        return repository.save(draft);
    }

    @Override
    public AdvertisementDraft setCity(Long userId, String value) {
        String text = normalized(value, 2, 100, "Город должен содержать от 2 до 100 символов.");
        return update(userId, AdvertisementCreationStep.WAITING_FOR_CITY,
                d -> d.setCity(text), AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE);
    }

    @Override
    public AdvertisementDraft chooseUsernameContact(Long userId, String username) {
        if (username == null || username.isBlank()) throw new DraftValidationException("Username не указан. Введите другой контакт.");
        String contact = username.startsWith("@") ? username : "@" + username;
        return update(userId, AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE,
                d -> d.setContact(contact), AdvertisementCreationStep.PREVIEW);
    }

    @Override
    public AdvertisementDraft requestCustomContact(Long userId) {
        AdvertisementDraft draft = requiredAt(userId, AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE);
        draft.setStep(AdvertisementCreationStep.WAITING_FOR_CUSTOM_CONTACT);
        return repository.save(draft);
    }

    @Override
    public AdvertisementDraft setCustomContact(Long userId, String value) {
        String text = normalized(value, 2, 255, "Контакт должен содержать от 2 до 255 символов.");
        return update(userId, AdvertisementCreationStep.WAITING_FOR_CUSTOM_CONTACT,
                d -> d.setContact(text), AdvertisementCreationStep.PREVIEW);
    }

    @Override
    public AdvertisementDraft moveToPreviousStep(Long userId) {
        AdvertisementDraft draft = repository.findByTelegramUserId(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Активный черновик не найден."));
        AdvertisementCreationStep previous = switch (draft.getStep()) {
            case WAITING_FOR_VEHICLE_BRAND -> AdvertisementCreationStep.WAITING_FOR_CATEGORY;
            case WAITING_FOR_VEHICLE_BRAND_SEARCH -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND;
            case WAITING_FOR_CUSTOM_VEHICLE_BRAND -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND;
            case WAITING_FOR_VEHICLE_MODEL -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND;
            case WAITING_FOR_VEHICLE_MODEL_SEARCH -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL;
            case WAITING_FOR_CUSTOM_VEHICLE_MODEL -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL;
            case WAITING_FOR_VEHICLE_YEAR -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL;
            case WAITING_FOR_VEHICLE_TRANSMISSION -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_YEAR;
            case WAITING_FOR_VEHICLE_ENGINE_TYPE -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_TRANSMISSION;
            case WAITING_FOR_VEHICLE_ENGINE_VOLUME -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_TYPE;
            case WAITING_FOR_VEHICLE_MILEAGE -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_TYPE;
            case WAITING_FOR_VEHICLE_DRIVE_TYPE -> AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE;
            case WAITING_FOR_TITLE -> AdvertisementCreationStep.WAITING_FOR_CATEGORY;
            case WAITING_FOR_DESCRIPTION -> AdvertisementCreationStep.WAITING_FOR_TITLE;
            case WAITING_FOR_PRICE -> AdvertisementCreationStep.WAITING_FOR_DESCRIPTION;
            case WAITING_FOR_PHOTO -> AdvertisementCreationStep.WAITING_FOR_PRICE;
            case WAITING_FOR_CITY -> AdvertisementCreationStep.WAITING_FOR_PHOTO;
            case WAITING_FOR_CONTACT_CHOICE -> AdvertisementCreationStep.WAITING_FOR_CITY;
            case WAITING_FOR_CUSTOM_CONTACT, PREVIEW -> AdvertisementCreationStep.WAITING_FOR_CONTACT_CHOICE;
            case WAITING_FOR_CATEGORY, NONE -> AdvertisementCreationStep.WAITING_FOR_CATEGORY;
        };
        draft.setStep(previous);
        log.info("Переход черновика назад, step={}", previous);
        return repository.save(draft);
    }

    @Override
    public AdvertisementDraft beginEdit(Long userId, AdvertisementCreationStep target) {
        AdvertisementDraft draft = requiredAt(userId, AdvertisementCreationStep.PREVIEW);
        var pending = advertisementRepository.findFirstByTelegramUserIdAndStatusOrderByCreatedAtDesc(
                userId, ru.murad.yourmarket.model.enums.AdvertisementStatus.WAITING_FOR_PAYMENT);
        if (pending.isPresent() && paymentRepository.findByAdvertisementId(pending.get().getId()).isPresent())
            throw new InvalidAdvertisementStateException("После создания счёта редактирование запрещено. Отмените сценарий и создайте новый.");
        draft.setEditMode(true);
        draft.setStep(target);
        if (target == AdvertisementCreationStep.WAITING_FOR_PHOTO) {
            photoRepository.deleteByDraftId(draft.getId());
            draft.setTelegramFileId(null);
        }
        return repository.save(draft);
    }

    @Override
    public void cancel(Long userId) {
        repository.findByTelegramUserId(userId).ifPresent(d -> { photoRepository.deleteByDraftId(d.getId()); vehicleDetailsRepository.deleteByAdvertisementDraftId(d.getId()); });
        repository.deleteByTelegramUserId(userId);
    }

    private AdvertisementDraft update(Long userId, AdvertisementCreationStep expected, Consumer<AdvertisementDraft> change,
                                      AdvertisementCreationStep next) {
        AdvertisementDraft draft = repository.findByTelegramUserId(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Активный черновик не найден."));
        if (draft.getStep() != expected) throw new InvalidAdvertisementStateException("Действие не соответствует текущему шагу.");
        change.accept(draft);
        if (draft.isEditMode()) {
            draft.setEditMode(false);
            draft.setStep(AdvertisementCreationStep.PREVIEW);
        } else draft.setStep(next);
        log.info("Смена шага черновика, step={}", draft.getStep());
        return repository.save(draft);
    }

    private AdvertisementDraft requiredAt(Long userId, AdvertisementCreationStep expected) {
        AdvertisementDraft draft = repository.findByTelegramUserId(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Активный черновик не найден."));
        if (draft.getStep() != expected) throw new InvalidAdvertisementStateException("Действие не соответствует текущему шагу.");
        return draft;
    }

    private String normalized(String value, int min, int max, String message) {
        String text = value == null ? "" : value.trim();
        if (text.length() < min || text.length() > max) throw new DraftValidationException(message);
        return text;
    }
}
