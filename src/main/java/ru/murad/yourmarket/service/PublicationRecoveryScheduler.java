package ru.murad.yourmarket.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.repository.AdvertisementRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicationRecoveryScheduler {
    private final AdvertisementRepository repository;
    private final PublicationTransactionService transactions;
    private final AdvertisementPublicationService publication;

    @Scheduled(fixedDelayString = "${publication.recovery-delay-ms:60000}")
    public void recover() {
        repository.findTop100ByStatusAndPublicationUpdatedAtLessThanEqualOrderByPublicationUpdatedAtAsc(
                        AdvertisementStatus.PUBLICATION_IN_PROGRESS, Instant.now().minusSeconds(120))
                .forEach(advertisement -> transactions.recoverStale(advertisement.getId()));

        // A successful payment may be committed immediately before a process restart.
        repository.findTop100ByStatusOrderByCreatedAtAsc(AdvertisementStatus.PAID)
                .forEach(advertisement -> {
                    try {
                        publication.publish(advertisement.getId());
                    } catch (RuntimeException exception) {
                        log.warn("Deferred publication remains pending advertisementId={}",
                                advertisement.getId());
                    }
                });
    }
}
