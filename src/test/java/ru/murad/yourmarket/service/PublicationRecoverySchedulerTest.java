package ru.murad.yourmarket.service;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.repository.AdvertisementRepository;

class PublicationRecoverySchedulerTest {
    @Test
    void paidAdvertisementIsPublishedAfterRestartRecovery() {
        AdvertisementRepository repository = mock(AdvertisementRepository.class);
        PublicationTransactionService transactions = mock(PublicationTransactionService.class);
        AdvertisementPublicationService publication = mock(AdvertisementPublicationService.class);
        UUID id = UUID.randomUUID();
        when(repository.findTop100ByStatusAndPublicationUpdatedAtLessThanEqualOrderByPublicationUpdatedAtAsc(
                eq(AdvertisementStatus.PUBLICATION_IN_PROGRESS), any())).thenReturn(List.of());
        when(repository.findTop100ByStatusOrderByCreatedAtAsc(AdvertisementStatus.PAID))
                .thenReturn(List.of(Advertisement.builder().id(id).build()));

        new PublicationRecoveryScheduler(repository, transactions, publication).recover();

        verify(publication).publish(id);
    }
}
