package ru.murad.yourmarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.repository.AdvertisementRepository;
import java.time.Instant;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class AdvertisementExpirationScheduler {
    private final AdvertisementRepository repository;
    private final AdvertisementExpirationService expirationService;
    private final AdvertisementLifecycleTransactionService lifecycle;
    private final ru.murad.yourmarket.config.PublicationProperties properties;

    @Scheduled(cron = "${publication.expiration-cron:0 0 * * * *}")
    public void expireAdvertisements() {
        repository.findTop100ByStatusAndExpirationStartedAtLessThanEqualOrderByExpirationStartedAtAsc(
                AdvertisementStatus.EXPIRATION_IN_PROGRESS,
                Instant.now().minusSeconds(properties.getExpirationClaimTimeoutSeconds()))
                .forEach(a -> lifecycle.recoverStaleExpiration(a.getId()));
        findBatch().forEach(expirationService::expire);
    }

    public java.util.List<UUID> findBatch() {
        return repository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                AdvertisementStatus.PUBLISHED, Instant.now()).stream().map(a -> a.getId()).toList();
    }
}
