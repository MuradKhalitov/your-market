package ru.murad.yourmarket.service.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.service.AdvertisementPublicationService;
import ru.murad.yourmarket.service.ModerationService;
import ru.murad.yourmarket.service.ModerationTransactionService;
import ru.murad.yourmarket.telegram.TelegramGateway;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {
    private final ModerationTransactionService transactions;
    private final AdvertisementPublicationService publication;
    private final TelegramGateway telegram;

    @Override
    public Integer submit(UUID id) {
        var claim = transactions.claimSubmission(id);
        if (!claim.sendAllowed()) {
            return claim.existingMessageId();
        }

        try {
            // Media IDs are durable progress. A retry after an administrator has explicitly
            // confirmed that no action message was created must never duplicate the media group.
            if (!transactions.hasSavedMedia(id)) {
                transactions.markCallStarted(id, claim.operationId());
                List<Integer> media = telegram.sendModerationMedia(claim.advertisement());
                transactions.saveModerationMedia(id, claim.operationId(), media);
            }
            transactions.markCallStarted(id, claim.operationId());
            Integer action = telegram.sendModerationAction(claim.advertisement());
            transactions.completeSubmission(id, claim.operationId(), action);
            return action;
        } catch (RuntimeException ex) {
            try {
                transactions.markSubmissionUnknown(id, claim.operationId());
            } catch (RuntimeException mark) {
                ex.addSuppressed(mark);
            }
            throw ex;
        }
    }

    @Override
    public AdvertisementResponseDto approve(UUID id, Long admin) {
        transactions.approve(id, admin);
        return publication.publish(id);
    }

    @Override
    public AdvertisementResponseDto reject(UUID id, Long admin, String reason) {
        return transactions.reject(id, admin, reason);
    }
}
