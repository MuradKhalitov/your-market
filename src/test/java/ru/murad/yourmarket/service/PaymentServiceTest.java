package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.dto.request.SuccessfulPaymentRequest;
import ru.murad.yourmarket.mapper.AdvertisementMapper;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.TelegramUser;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.model.enums.AdvertisementCreationStep;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.model.enums.InvoiceSendStatus;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.repository.AdvertisementRepository;
import ru.murad.yourmarket.repository.PaymentRepository;
import ru.murad.yourmarket.repository.TelegramUserRepository;
import ru.murad.yourmarket.service.impl.PaymentServiceImpl;

class PaymentServiceTest {

    private PublicationProperties publicationProperties(boolean moderationEnabled) {
        PublicationProperties properties = new PublicationProperties();
        properties.setPrice(new BigDecimal("199.00"));
        properties.setModerationEnabled(moderationEnabled);
        return properties;
    }

    AdvertisementDraftRepository drafts = mock(AdvertisementDraftRepository.class);
    AdvertisementRepository advertisements = mock(AdvertisementRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    TelegramUserRepository users = mock(TelegramUserRepository.class);
    AdvertisementDraftPhotoRepository draftPhotos = mock(AdvertisementDraftPhotoRepository.class);
    AdvertisementPhotoRepository adPhotos = mock(AdvertisementPhotoRepository.class);
    AdvertisementMapper mapper = mock(AdvertisementMapper.class);
    PaymentServiceImpl service = new PaymentServiceImpl(
        drafts,
        advertisements,
        payments,
        users,
        draftPhotos,
        adPhotos,
        mapper,
        publicationProperties(false)
    );

    @Test
    void createsPaymentWithConfiguredAmount() {
        AdvertisementDraft draft = completeDraft();
        Advertisement ad = ad(AdvertisementStatus.WAITING_FOR_PAYMENT);
        when(users.findByTelegramUserIdForUpdate(1L))
            .thenReturn(Optional.of(TelegramUser.builder().telegramUserId(1L).build()));
        when(drafts.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(draftPhotos.findByDraftIdOrderByPosition(any())).thenReturn(List.of());
        when(advertisements.findFirstByTelegramUserIdAndStatusOrderByCreatedAtDesc(anyLong(),
            any())).thenReturn(Optional.empty());
        when(mapper.toAdvertisement(draft, "user")).thenReturn(ad);
        when(advertisements.save(ad)).thenReturn(ad);
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
        Payment payment = service.createPaymentAndClaimInvoice(1L, "user").payment();
        assertEquals(new BigDecimal("199.00"), payment.getAmount());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        assertNotNull(payment.getPayload());
    }

    @Test
    void preCheckoutRejectsWrongAmount() {
        Payment payment = payment(PaymentStatus.CREATED);
        when(payments.findByPayloadForUpdate("payload")).thenReturn(Optional.of(payment));
        assertFalse(service.approvePreCheckout(1L, "payload", "RUB", 1L).approved());
        verify(advertisements, never()).findByIdForUpdate(any());
    }

    @Test
    void repeatedPreCheckoutIsRejected() {
        Payment payment = payment(PaymentStatus.PRE_CHECKOUT_APPROVED);
        when(payments.findByPayloadForUpdate("payload")).thenReturn(Optional.of(payment));
        assertFalse(service.approvePreCheckout(1L, "payload", "RUB", 19900L).approved());
        verify(advertisements, never()).findByIdForUpdate(any());
    }

    @Test
    void existingSentInvoiceCannotBeClaimedAgain() {
        AdvertisementDraft draft = completeDraft();
        Advertisement ad = ad(AdvertisementStatus.WAITING_FOR_PAYMENT);
        Payment payment = payment(PaymentStatus.CREATED);
        payment.setInvoiceSendStatus(InvoiceSendStatus.SENT);
        when(users.findByTelegramUserIdForUpdate(1L)).thenReturn(
            Optional.of(TelegramUser.builder().telegramUserId(1L).build()));
        when(drafts.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(advertisements.findFirstByTelegramUserIdAndStatusOrderByCreatedAtDesc(1L,
            AdvertisementStatus.WAITING_FOR_PAYMENT)).thenReturn(Optional.of(ad));
        when(payments.findByAdvertisementId(ad.getId())).thenReturn(Optional.of(payment));
        assertFalse(service.createPaymentAndClaimInvoice(1L, "user").sendAllowed());
        verify(payments, never()).save(any());
    }

    @Test
    void foreignInvoiceOperationCannotCompleteOrFail() {
        Payment payment = payment(PaymentStatus.CREATED);
        UUID owner = UUID.randomUUID();
        payment.setInvoiceSendStatus(InvoiceSendStatus.SENDING);
        payment.setInvoiceOperationId(owner);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        service.markInvoiceSent(payment.getId(), UUID.randomUUID());
        service.releaseInvoiceClaim(payment.getId(), UUID.randomUUID());
        assertEquals(InvoiceSendStatus.SENDING, payment.getInvoiceSendStatus());
        assertEquals(owner, payment.getInvoiceOperationId());
    }

    @Test
    void ambiguousInvoiceBecomesUnknown() {
        Payment payment = payment(PaymentStatus.CREATED);
        UUID owner = UUID.randomUUID();
        payment.setInvoiceSendStatus(InvoiceSendStatus.SENDING);
        payment.setInvoiceOperationId(owner);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        service.markInvoiceUnknown(payment.getId(), owner);
        assertEquals(InvoiceSendStatus.SEND_UNKNOWN, payment.getInvoiceSendStatus());
        assertNull(payment.getInvoiceOperationId());
    }

    @Test
    void successfulPaymentChangesStatuses() {
        Payment payment = payment(PaymentStatus.PRE_CHECKOUT_APPROVED);
        Advertisement ad = ad(AdvertisementStatus.WAITING_FOR_PAYMENT);
        when(payments.findByPayloadForUpdate("payload")).thenReturn(Optional.of(payment));
        when(advertisements.findByIdForUpdate(ad.getId())).thenReturn(Optional.of(ad));
        var result = service.processSuccessfulPayment(request());
        assertTrue(result.newlyProcessed());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(AdvertisementStatus.PAID, ad.getStatus());
        verify(drafts).deleteByTelegramUserId(1L);
    }

    @Test
    void repeatedSuccessfulPaymentIsIgnored() {
        Payment payment = payment(PaymentStatus.SUCCEEDED);
        Advertisement ad = ad(AdvertisementStatus.PAID);
        when(payments.findByPayloadForUpdate("payload")).thenReturn(Optional.of(payment));
        when(advertisements.findByIdForUpdate(ad.getId())).thenReturn(Optional.of(ad));
        assertFalse(service.processSuccessfulPayment(request()).newlyProcessed());
        verify(payments, never()).save(any());
    }

    @Test
    void moderationEnabledMovesToWaitingForModeration() {
        Payment payment = payment(PaymentStatus.PRE_CHECKOUT_APPROVED);
        Advertisement advertisement = ad(AdvertisementStatus.WAITING_FOR_PAYMENT);

        when(payments.findByPayloadForUpdate("payload"))
            .thenReturn(Optional.of(payment));
        when(advertisements.findByIdForUpdate(advertisement.getId()))
            .thenReturn(Optional.of(advertisement));

        PaymentServiceImpl moderated = new PaymentServiceImpl(
            drafts,
            advertisements,
            payments,
            users,
            draftPhotos,
            adPhotos,
            mapper,
            publicationProperties(true)
        );

        moderated.processSuccessfulPayment(request());

        assertEquals(
            AdvertisementStatus.WAITING_FOR_MODERATION,
            advertisement.getStatus()
        );
    }

    private SuccessfulPaymentRequest request() {
        return new SuccessfulPaymentRequest(1L, "payload", "RUB", 19900L, "tg-charge",
            "provider-charge");
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder().id(UUID.randomUUID()).advertisementId(ADVERTISEMENT_ID)
            .telegramUserId(1L)
            .payload("payload").amount(new BigDecimal("199.00")).currency("RUB").status(status)
            .build();
    }

    private Advertisement ad(AdvertisementStatus status) {
        return Advertisement.builder().id(ADVERTISEMENT_ID).telegramUserId(1L).chatId(10L)
            .status(status).build();
    }

    private AdvertisementDraft completeDraft() {
        return AdvertisementDraft.builder().telegramUserId(1L).chatId(10L)
            .step(AdvertisementCreationStep.PREVIEW)
            .category(AdvertisementCategory.AUTO).title("Автомобиль")
            .description("Хорошее состояние")
            .itemPrice(BigDecimal.TEN).telegramFileId("file").city("Москва").contact("@seller")
            .build();
    }

    private static final UUID ADVERTISEMENT_ID = UUID.randomUUID();
}
