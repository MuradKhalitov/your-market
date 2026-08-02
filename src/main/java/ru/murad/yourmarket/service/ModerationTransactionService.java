package ru.murad.yourmarket.service;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.model.Advertisement;
import java.util.UUID;
public interface ModerationTransactionService {
    SubmitClaim claimSubmission(UUID id);
    void markCallStarted(UUID id,UUID operationId);
    void saveModerationMedia(UUID id,UUID operationId,java.util.List<Integer> messageIds);
    void completeSubmission(UUID id,UUID operationId,Integer messageId);
    void markSubmissionUnknown(UUID id,UUID operationId);
    Integer resolveSubmission(UUID id, UUID operationId, boolean moderationMessageConfirmed, Integer messageId);
    boolean hasSavedMedia(UUID id);
    void approve(UUID id, Long adminId);
    AdvertisementResponseDto reject(UUID id, Long adminId, String reason);
    record SubmitClaim(Advertisement advertisement, UUID operationId, boolean sendAllowed, Integer existingMessageId) {}
}
