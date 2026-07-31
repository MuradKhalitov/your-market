package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.request.SuccessfulPaymentRequest;
import ru.murad.yourmarket.dto.response.PreCheckoutResult;
import ru.murad.yourmarket.model.Payment;
import java.util.UUID;

public interface PaymentService {
    InvoiceClaim createPaymentAndClaimInvoice(Long telegramUserId, String username);
    void markInvoiceSent(UUID paymentId, UUID operationId);
    void releaseInvoiceClaim(UUID paymentId, UUID operationId);
    void markInvoiceUnknown(UUID paymentId, UUID operationId);
    void resolveInvoice(UUID paymentId, boolean retryAllowed);
    PreCheckoutResult approvePreCheckout(Long telegramUserId, String payload, String currency, Long totalAmount);
    SuccessfulPaymentResult processSuccessfulPayment(SuccessfulPaymentRequest request);
    record SuccessfulPaymentResult(UUID advertisementId, boolean newlyProcessed) {}
    record InvoiceClaim(Payment payment, UUID operationId, boolean sendAllowed, boolean unknown) {}
}
