package ru.murad.yourmarket.service;

import java.util.UUID;

public interface PaymentRefundTransactionService {
    RefundClaim claim(UUID paymentId);
    void complete(UUID paymentId, UUID operationId);
    void release(UUID paymentId, UUID operationId);
    void markReconciliationRequired(UUID paymentId, UUID operationId, String safeReason);
    void recoverStaleClaims();
    void resolve(UUID paymentId, boolean refundConfirmed);

    record RefundClaim(UUID paymentId, UUID operationId, Long telegramUserId,
                       String telegramPaymentChargeId, boolean claimed) { }
}
