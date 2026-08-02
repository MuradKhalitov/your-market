package ru.murad.yourmarket.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.enums.TelegramMessageType;
import ru.murad.yourmarket.service.impl.AdvertisementPublicationServiceImpl;
import ru.murad.yourmarket.telegram.TelegramGateway;

class AdvertisementPublicationServiceTest {
    private final PublicationTransactionService transactions = mock(PublicationTransactionService.class);
    private final TelegramGateway telegram = mock(TelegramGateway.class);
    private final AdvertisementPublicationServiceImpl service = new AdvertisementPublicationServiceImpl(transactions, telegram,
            mock(OperationalMetrics.class));
    private final UUID advertisementId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    private final Advertisement advertisement = Advertisement.builder().id(advertisementId).build();

    @Test
    void mediaGroupSavesExactlyReturnedMessageIdsAndCompletesWithoutActionMessage() {
        when(transactions.claim(advertisementId, false)).thenReturn(
                new PublicationTransactionService.Claim(advertisement, operationId, true, null));
        when(telegram.publishAdvertisementPrimaryMessages(advertisement)).thenReturn(List.of(21, 22));
        when(telegram.needsSeparateContactMessage(advertisement)).thenReturn(false);

        service.publish(advertisementId);

        var order = inOrder(transactions, telegram);
        order.verify(telegram).publishAdvertisementPrimaryMessages(advertisement);
        order.verify(transactions).saveProgress(advertisementId, operationId, List.of(21, 22),
                TelegramMessageType.MEDIA_GROUP_ITEM, true);
        order.verify(transactions).complete(advertisementId, operationId);
        verify(telegram, never()).publishAdvertisementContactMessage(advertisement);
    }

    @Test
    void singlePhotoSavesExactlyOneMessageId() {
        when(transactions.claim(advertisementId, false)).thenReturn(
                new PublicationTransactionService.Claim(advertisement, operationId, true, null));
        when(telegram.publishAdvertisementPrimaryMessages(advertisement)).thenReturn(List.of(11));
        when(telegram.needsSeparateContactMessage(advertisement)).thenReturn(false);

        service.publish(advertisementId);

        verify(transactions).saveProgress(advertisementId, operationId, List.of(11), TelegramMessageType.PHOTO, true);
        verify(telegram, never()).publishAdvertisementContactMessage(advertisement);
    }

    @Test
    void concurrentWorkerDoesNotPublish() {
        when(transactions.claim(advertisementId, false)).thenReturn(
                new PublicationTransactionService.Claim(advertisement, operationId, false, mock(AdvertisementResponseDto.class)));
        service.publish(advertisementId);
        verifyNoInteractions(telegram);
    }
}
