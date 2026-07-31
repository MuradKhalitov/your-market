package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.model.Advertisement;
import java.util.List;
import java.util.UUID;

public interface PublicationTransactionService {
    Claim claim(UUID advertisementId, boolean fromModeration);
    void markTelegramCallStarted(UUID advertisementId, UUID operationId);
    void saveProgress(UUID advertisementId, UUID operationId, List<Integer> messageIds, ru.murad.yourmarket.model.enums.TelegramMessageType type, boolean finalResult);
    AdvertisementResponseDto complete(UUID advertisementId, UUID operationId);
    void fail(UUID advertisementId, UUID operationId, String reason);
    void reconciliationRequired(UUID advertisementId, UUID operationId, String reason);
    AdvertisementResponseDto resolve(UUID advertisementId, Resolution resolution, Integer verifiedMessageId);
    void recoverStale(UUID advertisementId);
    record Claim(Advertisement advertisement, UUID operationId, boolean shouldPublish, AdvertisementResponseDto existing) {}
    enum Resolution { MARK_PUBLISHED, RETRY_AFTER_VERIFICATION }
}
