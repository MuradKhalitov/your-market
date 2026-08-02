package ru.murad.yourmarket.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.exception.AdvertisementNotFoundException;
import ru.murad.yourmarket.exception.InvalidAdvertisementStateException;
import ru.murad.yourmarket.exception.InvalidPaymentStateException;
import ru.murad.yourmarket.exception.PaymentNotFoundException;
import ru.murad.yourmarket.mapper.AdvertisementMapper;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.ModerationTelegramMessage;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.model.enums.ModerationPhase;
import ru.murad.yourmarket.model.enums.ModerationSubmissionStatus;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.repository.AdvertisementRepository;
import ru.murad.yourmarket.repository.ModerationTelegramMessageRepository;
import ru.murad.yourmarket.repository.PaymentRepository;
import ru.murad.yourmarket.service.AdminAccessService;
import ru.murad.yourmarket.service.ModerationTransactionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationTransactionServiceImpl implements ModerationTransactionService {
    private final AdvertisementRepository advertisements;
    private final PaymentRepository payments;
    private final AdminAccessService access;
    private final AdvertisementMapper mapper;
    private final ModerationTelegramMessageRepository messages;

    @Override @Transactional
    public SubmitClaim claimSubmission(UUID id) {
        Advertisement ad = lock(id);
        if (ad.getStatus() != AdvertisementStatus.WAITING_FOR_MODERATION) throw new InvalidAdvertisementStateException("Not waiting for moderation.");
        if (ad.getModerationSubmissionStatus() == ModerationSubmissionStatus.SUBMITTED) return new SubmitClaim(ad, null, false, ad.getModerationMessageId());
        if (ad.getModerationSubmissionStatus() == ModerationSubmissionStatus.SENDING) {
            if (ad.getModerationSendingSince() != null && ad.getModerationSendingSince().isBefore(Instant.now().minusSeconds(120))) {
                if (ad.getModerationPhase() == ModerationPhase.CLAIMED && messages.findByAdvertisementIdOrderByPosition(id).isEmpty()) {
                    ad.setModerationSubmissionStatus(ModerationSubmissionStatus.NOT_SUBMITTED); clear(ad);
                } else {
                    ad.setModerationSubmissionStatus(ModerationSubmissionStatus.SEND_UNKNOWN);
                    ad.setModerationPhase(ModerationPhase.RECONCILIATION_REQUIRED);
                }
            }
            return new SubmitClaim(ad, null, false, ad.getModerationMessageId());
        }
        if (ad.getModerationSubmissionStatus() == ModerationSubmissionStatus.SEND_UNKNOWN) return new SubmitClaim(ad, null, false, ad.getModerationMessageId());
        UUID op = UUID.randomUUID();
        ad.setModerationSubmissionStatus(ModerationSubmissionStatus.SENDING);
        ad.setModerationSendingSince(Instant.now());
        ad.setModerationOperationId(op);
        ad.setModerationPhase(ModerationPhase.CLAIMED);
        return new SubmitClaim(advertisements.save(ad), op, true, null);
    }

    @Override @Transactional public void markCallStarted(UUID id, UUID op) { operation(id, op).setModerationPhase(ModerationPhase.TELEGRAM_CALL_STARTED); }
    @Override @Transactional public void saveModerationMedia(UUID id, UUID op, List<Integer> ids) {
        Advertisement ad = operation(id, op); int start = messages.findByAdvertisementIdOrderByPosition(id).size();
        List<ModerationTelegramMessage> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) rows.add(ModerationTelegramMessage.builder().advertisementId(id).moderationOperationId(op).messageId(ids.get(i)).position(start + i).build());
        messages.saveAllAndFlush(rows); ad.setModerationPhase(ModerationPhase.MEDIA_SENT);
    }
    @Override @Transactional public void completeSubmission(UUID id, UUID op, Integer messageId) {
        Advertisement ad = operation(id, op); ad.setModerationMessageId(messageId); ad.setModerationSubmittedAt(Instant.now());
        ad.setModerationSubmissionStatus(ModerationSubmissionStatus.SUBMITTED); ad.setModerationPhase(ModerationPhase.COMPLETED); clearOperation(ad);
    }
    @Override @Transactional public void markSubmissionUnknown(UUID id, UUID op) {
        Advertisement ad = operation(id, op); ad.setModerationSubmissionStatus(ModerationSubmissionStatus.SEND_UNKNOWN);
        ad.setModerationPhase(ModerationPhase.RECONCILIATION_REQUIRED); ad.setModerationSendingSince(null);
    }

    @Override @Transactional
    public Integer resolveSubmission(UUID id, UUID op, boolean confirmed, Integer messageId) {
        Advertisement ad = lock(id);
        if (ad.getModerationSubmissionStatus() == ModerationSubmissionStatus.SUBMITTED) return ad.getModerationMessageId();
        if (ad.getModerationSubmissionStatus() != ModerationSubmissionStatus.SEND_UNKNOWN || !Objects.equals(op, ad.getModerationOperationId())) {
            throw new InvalidAdvertisementStateException("Moderation reconciliation is not available for this operation.");
        }
        if (confirmed) {
            if (messageId == null || messageId <= 0) throw new InvalidAdvertisementStateException("A positive moderation messageId is required.");
            ad.setModerationMessageId(messageId); ad.setModerationSubmittedAt(Instant.now());
            ad.setModerationSubmissionStatus(ModerationSubmissionStatus.SUBMITTED); ad.setModerationPhase(ModerationPhase.COMPLETED); clearOperation(ad);
        } else {
            ad.setModerationSubmissionStatus(ModerationSubmissionStatus.NOT_SUBMITTED); clear(ad);
        }
        log.info("Moderation reconciliation advertisementId={}, operationId={}, resolution={}, resultingStatus={}",
                id, op, confirmed ? "CONFIRMED" : "RETRY_ALLOWED", ad.getModerationSubmissionStatus());
        return ad.getModerationMessageId();
    }

    @Override @Transactional(readOnly = true)
    public boolean hasSavedMedia(UUID id) { return !messages.findByAdvertisementIdOrderByPosition(id).isEmpty(); }
    @Override @Transactional public void approve(UUID id, Long admin) { validateAdmin(admin); decision(id).setStatus(AdvertisementStatus.PAID); }
    @Override @Transactional public AdvertisementResponseDto reject(UUID id, Long admin, String reason) { validateAdmin(admin); Advertisement ad = decision(id); ad.setStatus(AdvertisementStatus.REJECTED); ad.setRejectedAt(Instant.now()); ad.setRejectionReason(reason); return mapper.toResponse(ad); }
    private Advertisement decision(UUID id) { Advertisement ad = lock(id); if (ad.getStatus() != AdvertisementStatus.WAITING_FOR_MODERATION) throw new InvalidAdvertisementStateException("Decision already made."); Payment payment = payments.findByAdvertisementId(id).orElseThrow(PaymentNotFoundException::new); if (payment.getStatus() != PaymentStatus.SUCCEEDED) throw new InvalidPaymentStateException("Payment is not successful."); return ad; }
    private Advertisement operation(UUID id, UUID op) { Advertisement ad = lock(id); if (ad.getModerationSubmissionStatus() != ModerationSubmissionStatus.SENDING || !Objects.equals(op, ad.getModerationOperationId())) throw new InvalidAdvertisementStateException("Moderation operation already completed."); return ad; }
    private Advertisement lock(UUID id) { return advertisements.findByIdForUpdate(id).orElseThrow(AdvertisementNotFoundException::new); }
    private void validateAdmin(Long id) { if (!access.isAdmin(id)) throw new InvalidAdvertisementStateException("Insufficient privileges."); }
    private void clear(Advertisement ad) { ad.setModerationOperationId(null); ad.setModerationSendingSince(null); ad.setModerationPhase(null); }
    private void clearOperation(Advertisement ad) { ad.setModerationOperationId(null); ad.setModerationSendingSince(null); }
}
