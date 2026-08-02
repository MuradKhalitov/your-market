package ru.murad.yourmarket.service;

import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.model.enums.AdvertisementCreationStep;
import java.util.Optional;

public interface AdvertisementDraftService {
    AdvertisementDraft startCreation(Long telegramUserId, Long chatId);
    Optional<AdvertisementDraft> findActive(Long telegramUserId);
    AdvertisementDraft setCategory(Long userId, AdvertisementCategory value);
    AdvertisementDraft setTitle(Long userId, String value);
    AdvertisementDraft setDescription(Long userId, String value);
    AdvertisementDraft setPrice(Long userId, String value);
    AdvertisementDraft setPhoto(Long userId, String fileId);
    AdvertisementDraft addPhoto(Long userId, String fileId);
    AdvertisementDraft finishPhotos(Long userId);
    AdvertisementDraft clearPhotos(Long userId);
    AdvertisementDraft chooseUsernameContact(Long userId, String username);
    AdvertisementDraft requestCustomContact(Long userId);
    AdvertisementDraft setCustomContact(Long userId, String value);
    AdvertisementDraft moveToPreviousStep(Long telegramUserId);
    AdvertisementDraft beginEdit(Long telegramUserId, AdvertisementCreationStep targetStep);
    void cancel(Long telegramUserId);
}
