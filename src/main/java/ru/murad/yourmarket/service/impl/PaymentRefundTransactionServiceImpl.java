package ru.murad.yourmarket.service.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.exception.InvalidPaymentStateException;
import ru.murad.yourmarket.exception.PaymentNotFoundException;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.repository.PaymentRepository;
import ru.murad.yourmarket.service.PaymentRefundTransactionService;
import ru.murad.yourmarket.config.PublicationProperties;

@Service
@RequiredArgsConstructor
public class PaymentRefundTransactionServiceImpl implements PaymentRefundTransactionService {
    private final PaymentRepository payments;
    private final PublicationProperties publicationProperties;

    @Override
    @Transactional
    public RefundClaim claim(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_IN_PROGRESS
                || payment.getStatus() == PaymentStatus.REFUND_RECONCILIATION_REQUIRED) {
            return new RefundClaim(paymentId, null, payment.getTelegramUserId(), payment.getTelegramPaymentChargeId(), false);
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED || !"XTR".equals(payment.getCurrency())
                || payment.getTelegramPaymentChargeId() == null || payment.getTelegramPaymentChargeId().isBlank()) {
            throw new InvalidPaymentStateException("Возврат Telegram Stars недоступен для этого платежа.");
        }
        UUID operationId = UUID.randomUUID();
        payment.setStatus(PaymentStatus.REFUND_IN_PROGRESS);
        payment.setRefundOperationId(operationId);
        payment.setRefundStartedAt(Instant.now());
        return new RefundClaim(paymentId, operationId, payment.getTelegramUserId(), payment.getTelegramPaymentChargeId(), true);
    }

    @Override
    @Transactional
    public void complete(UUID paymentId, UUID operationId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.REFUND_IN_PROGRESS && operationId.equals(payment.getRefundOperationId())) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundOperationId(null);
            payment.setRefundStartedAt(null);
        }
    }

    @Override
    @Transactional
    public void release(UUID paymentId, UUID operationId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.REFUND_IN_PROGRESS && operationId.equals(payment.getRefundOperationId())) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setRefundOperationId(null);
            payment.setRefundStartedAt(null);
        }
    }

    @Override
    @Transactional
    public void markReconciliationRequired(UUID paymentId, UUID operationId, String safeReason) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.REFUND_IN_PROGRESS
                && operationId.equals(payment.getRefundOperationId())) {
            payment.setStatus(PaymentStatus.REFUND_RECONCILIATION_REQUIRED);
            payment.setFailureReason(safe(safeReason));
        }
    }

    @Override
    @Transactional
    public void recoverStaleClaims() {
        Instant cutoff = Instant.now().minusSeconds(publicationProperties.getRefundClaimTimeoutSeconds());
        payments.findTop100ByStatusAndRefundStartedAtLessThanEqualOrderByRefundStartedAtAsc(
                        PaymentStatus.REFUND_IN_PROGRESS, cutoff)
                .forEach(payment -> {
                    Payment locked = payments.findByIdForUpdate(payment.getId()).orElse(null);
                    if (locked != null && locked.getStatus() == PaymentStatus.REFUND_IN_PROGRESS
                            && locked.getRefundStartedAt() != null && !locked.getRefundStartedAt().isAfter(cutoff)) {
                        locked.setStatus(PaymentStatus.REFUND_RECONCILIATION_REQUIRED);
                        locked.setFailureReason("Refund outcome is unknown after claim timeout");
                    }
                });
    }

    @Override
    @Transactional
    public void resolve(UUID paymentId, boolean refundConfirmed) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() != PaymentStatus.REFUND_RECONCILIATION_REQUIRED) {
            throw new InvalidPaymentStateException("Refund reconciliation is not required for this payment.");
        }
        payment.setStatus(refundConfirmed ? PaymentStatus.REFUNDED : PaymentStatus.SUCCEEDED);
        payment.setRefundOperationId(null);
        payment.setRefundStartedAt(null);
        if (refundConfirmed) {
            payment.setFailureReason(null);
        }
    }

    private String safe(String value) {
        return value == null ? "Telegram refund outcome is unknown"
                : value.substring(0, Math.min(255, value.length()));
    }
}
