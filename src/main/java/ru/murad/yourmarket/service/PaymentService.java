package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.request.SuccessfulPaymentRequest;
import ru.murad.yourmarket.dto.response.PreCheckoutResult;
import ru.murad.yourmarket.model.Payment;
import java.util.UUID;

public interface PaymentService {
    InvoiceClaim createPaymentAndClaimInvoice(Long telegramUserId, String username);
    void markInvoiceSent(UUID paymentId, UUID operationId);
    void failInvoiceSending(UUID paymentId, UUID operationId, String safeReason);
    void markInvoiceUnknown(UUID paymentId, UUID operationId);
    void resolveInvoice(UUID paymentId, boolean retryAllowed);
    PreCheckoutResult approvePreCheckout(Long telegramUserId, String payload, String currency, Long totalAmount);
    SuccessfulPaymentResult processSuccessfulPayment(SuccessfulPaymentRequest request);
    record SuccessfulPaymentResult(UUID advertisementId, boolean newlyProcessed) {}
    enum InvoiceClaimResult { CLAIMED, ALREADY_SENT, IN_PROGRESS, UNKNOWN }
    record InvoiceClaim(Payment payment, UUID operationId, InvoiceClaimResult result) {
        public boolean sendAllowed() { return result == InvoiceClaimResult.CLAIMED; }
        public boolean unknown() { return result == InvoiceClaimResult.UNKNOWN; }
    }
}
