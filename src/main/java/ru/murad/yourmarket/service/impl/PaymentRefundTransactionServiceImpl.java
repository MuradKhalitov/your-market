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

@Service
@RequiredArgsConstructor
public class PaymentRefundTransactionServiceImpl implements PaymentRefundTransactionService {
    private final PaymentRepository payments;

    @Override
    @Transactional
    public RefundClaim claim(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_IN_PROGRESS) {
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
}
