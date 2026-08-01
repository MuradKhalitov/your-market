package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.dto.request.SuccessfulPaymentRequest;
import ru.murad.yourmarket.dto.response.PreCheckoutResult;
import ru.murad.yourmarket.exception.*;
import ru.murad.yourmarket.mapper.AdvertisementMapper;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.*;
import ru.murad.yourmarket.service.PaymentService;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final AdvertisementDraftRepository draftRepository;
    private final AdvertisementRepository advertisementRepository;
    private final PaymentRepository paymentRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final AdvertisementDraftPhotoRepository draftPhotoRepository;
    private final AdvertisementPhotoRepository photoRepository;
    private final AdvertisementMapper mapper;
    private final PublicationProperties properties;

    @Override @Transactional
    public InvoiceClaim createPaymentAndClaimInvoice(Long userId, String username) {
        telegramUserRepository.findByTelegramUserIdForUpdate(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Пользователь Telegram не найден."));
        AdvertisementDraft draft = draftRepository.findByTelegramUserId(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Черновик не найден."));
        validateComplete(draft);
        Advertisement pending = advertisementRepository
                .findFirstByTelegramUserIdAndStatusOrderByCreatedAtDesc(userId, AdvertisementStatus.WAITING_FOR_PAYMENT)
                .orElse(null);
        if (pending != null) {
            Payment activePayment = paymentRepository.findByAdvertisementIdForUpdate(pending.getId()).orElse(null);
            if (activePayment != null && (activePayment.getStatus() == PaymentStatus.CREATED
                    || activePayment.getStatus() == PaymentStatus.PRE_CHECKOUT_APPROVED)) return claimInvoice(activePayment);
        }
        Advertisement advertisement = mapper.toAdvertisement(draft, username);
        advertisement = advertisementRepository.save(advertisement);
        var draftPhotos = draftPhotoRepository.findByDraftIdOrderByPosition(draft.getId());
        if (draftPhotos.isEmpty() && draft.getTelegramFileId() != null) {
            photoRepository.save(AdvertisementPhoto.builder().advertisementId(advertisement.getId())
                    .telegramFileId(draft.getTelegramFileId()).position(0).build());
        } else {
            UUID adId = advertisement.getId();
            photoRepository.saveAll(draftPhotos.stream().map(p -> AdvertisementPhoto.builder()
                    .advertisementId(adId).telegramFileId(p.getTelegramFileId()).position(p.getPosition()).build()).toList());
        }
        Payment payment = Payment.builder()
                .advertisementId(advertisement.getId()).telegramUserId(userId)
                .payload(UUID.randomUUID().toString()).amount(properties.getPrice())
                .currency(properties.getCurrency()).status(PaymentStatus.CREATED)
                .invoiceSendStatus(InvoiceSendStatus.NOT_SENT).build();
        payment = paymentRepository.save(payment);
        log.info("Создан платёж paymentId={}, advertisementId={}, telegramUserId={}",
                payment.getId(), advertisement.getId(), userId);
        return claimInvoice(payment);
    }

    private InvoiceClaim claimInvoice(Payment payment) {
        Instant staleBefore = Instant.now().minusSeconds(120);
        boolean stale = payment.getInvoiceSendStatus() == InvoiceSendStatus.SENDING
                && payment.getInvoiceSendingSince() != null && payment.getInvoiceSendingSince().isBefore(staleBefore);
        if (stale) {
            payment.setInvoiceSendStatus(InvoiceSendStatus.SEND_UNKNOWN);
            payment.setInvoiceSendingSince(null);
            payment.setInvoiceOperationId(null);
            Payment saved = paymentRepository.save(payment);
            logClaim(saved, null, InvoiceClaimResult.UNKNOWN);
            return new InvoiceClaim(saved, null, InvoiceClaimResult.UNKNOWN);
        }
        if (payment.getInvoiceSendStatus() == InvoiceSendStatus.NOT_SENT) {
            payment.setInvoiceSendStatus(InvoiceSendStatus.SENDING);
            payment.setInvoiceSendingSince(Instant.now());
            UUID operationId=UUID.randomUUID(); payment.setInvoiceOperationId(operationId);
            Payment saved = paymentRepository.save(payment);
            logClaim(saved, operationId, InvoiceClaimResult.CLAIMED);
            return new InvoiceClaim(saved, operationId, InvoiceClaimResult.CLAIMED);
        }
        InvoiceClaimResult result = switch (payment.getInvoiceSendStatus()) {
            case SENT -> InvoiceClaimResult.ALREADY_SENT;
            case SENDING -> InvoiceClaimResult.IN_PROGRESS;
            case SEND_UNKNOWN -> InvoiceClaimResult.UNKNOWN;
            case NOT_SENT -> throw new IllegalStateException("NOT_SENT должен быть захвачен до формирования результата");
        };
        logClaim(payment, payment.getInvoiceOperationId(), result);
        return new InvoiceClaim(payment, payment.getInvoiceOperationId(), result);
    }

    private void logClaim(Payment payment, UUID operationId, InvoiceClaimResult result) {
        log.info("Invoice claim paymentId={}, advertisementId={}, invoiceSendStatus={}, operationId={}, result={}",
                payment.getId(), payment.getAdvertisementId(), payment.getInvoiceSendStatus(), operationId, result);
    }

    @Override @Transactional
    public void markInvoiceSent(UUID paymentId, UUID operationId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getInvoiceSendStatus() == InvoiceSendStatus.SENDING && operationId.equals(payment.getInvoiceOperationId())) {
            payment.setInvoiceSendStatus(InvoiceSendStatus.SENT);
            payment.setInvoiceSentAt(Instant.now());
            payment.setInvoiceSendingSince(null);
            payment.setInvoiceOperationId(null);
        }
    }

    @Override @Transactional
    public void releaseInvoiceClaim(UUID paymentId, UUID operationId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getInvoiceSendStatus() == InvoiceSendStatus.SENDING && operationId.equals(payment.getInvoiceOperationId())) {
            payment.setInvoiceSendStatus(InvoiceSendStatus.NOT_SENT);
            payment.setInvoiceSendingSince(null);
            payment.setInvoiceOperationId(null);
        }
    }

    @Override @Transactional public void markInvoiceUnknown(UUID paymentId,UUID operationId){Payment p=paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);if(p.getInvoiceSendStatus()==InvoiceSendStatus.SENDING&&operationId.equals(p.getInvoiceOperationId())){p.setInvoiceSendStatus(InvoiceSendStatus.SEND_UNKNOWN);p.setInvoiceSendingSince(null);p.setInvoiceOperationId(null);}}
    @Override @Transactional public void resolveInvoice(UUID paymentId,boolean retryAllowed){Payment p=paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);if(p.getInvoiceSendStatus()!=InvoiceSendStatus.SEND_UNKNOWN)throw new InvalidPaymentStateException("Invoice reconciliation не требуется");p.setInvoiceSendStatus(retryAllowed?InvoiceSendStatus.NOT_SENT:InvoiceSendStatus.SENT);if(!retryAllowed)p.setInvoiceSentAt(Instant.now());log.info("Invoice reconciliation paymentId={}, retryAllowed={}",paymentId,retryAllowed);}

    @Override @Transactional
    public PreCheckoutResult approvePreCheckout(Long userId, String payload, String currency, Long totalAmount) {
        Payment payment = paymentRepository.findByPayloadForUpdate(payload).orElse(null);
        if (payment == null) return PreCheckoutResult.reject("Платёж не найден. Создайте счёт заново.");
        if (!payment.getTelegramUserId().equals(userId)) return PreCheckoutResult.reject("Этот счёт создан для другого пользователя.");
        if (payment.getStatus() != PaymentStatus.CREATED)
            return PreCheckoutResult.reject("Платёж уже обработан или недоступен.");
        if (!payment.getCurrency().equals(currency) || expectedMinor(payment) != totalAmount)
            return PreCheckoutResult.reject("Сумма или валюта платежа не совпадает.");
        Advertisement ad = advertisementRepository.findByIdForUpdate(payment.getAdvertisementId()).orElse(null);
        if (ad == null || ad.getStatus() != AdvertisementStatus.WAITING_FOR_PAYMENT)
            return PreCheckoutResult.reject("Объявление больше не ожидает оплату.");
        payment.setStatus(PaymentStatus.PRE_CHECKOUT_APPROVED);
        paymentRepository.save(payment);
        return PreCheckoutResult.approve();
    }

    @Override @Transactional
    public SuccessfulPaymentResult processSuccessfulPayment(SuccessfulPaymentRequest request) {
        Payment payment = paymentRepository.findByPayloadForUpdate(request.payload())
                .orElseThrow(PaymentNotFoundException::new);
        Advertisement ad = advertisementRepository.findByIdForUpdate(payment.getAdvertisementId())
                .orElseThrow(AdvertisementNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED)
            return new SuccessfulPaymentResult(ad.getId(), false);
        if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PRE_CHECKOUT_APPROVED)
            throw new InvalidPaymentStateException("Платёж находится в недопустимом состоянии.");
        if (!payment.getTelegramUserId().equals(request.telegramUserId())
                || !payment.getCurrency().equals(request.currency())
                || expectedMinor(payment) != request.totalAmount())
            throw new InvalidPaymentStateException("Данные успешного платежа не прошли проверку.");
        if (ad.getStatus() != AdvertisementStatus.WAITING_FOR_PAYMENT)
            throw new InvalidAdvertisementStateException("Объявление не ожидает оплату.");
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTelegramPaymentChargeId(request.telegramChargeId());
        payment.setProviderPaymentChargeId(request.providerChargeId());
        payment.setPaidAt(now);
        ad.setStatus(properties.isModerationEnabled()
                ? AdvertisementStatus.WAITING_FOR_MODERATION : AdvertisementStatus.PAID);
        ad.setPaidAt(now);
        paymentRepository.save(payment);
        advertisementRepository.save(ad);
        draftRepository.findByTelegramUserId(request.telegramUserId())
                .ifPresent(d -> draftPhotoRepository.deleteByDraftId(d.getId()));
        draftRepository.deleteByTelegramUserId(request.telegramUserId());
        log.info("Платёж подтверждён paymentId={}, advertisementId={}, telegramUserId={}",
                payment.getId(), ad.getId(), request.telegramUserId());
        return new SuccessfulPaymentResult(ad.getId(), true);
    }

    private long expectedMinor(Payment payment) {
        return payment.getAmount().movePointRight(2).longValueExact();
    }

    private void validateComplete(AdvertisementDraft d) {
        if (d.getStep() != AdvertisementCreationStep.PREVIEW || d.getCategory() == null || d.getTitle() == null
                || d.getDescription() == null || d.getItemPrice() == null || d.getTelegramFileId() == null
                || d.getCity() == null || d.getContact() == null)
            throw new InvalidAdvertisementStateException("Заполните все поля объявления перед оплатой.");
    }
}
