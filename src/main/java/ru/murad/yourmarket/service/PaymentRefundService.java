package ru.murad.yourmarket.service;

import java.util.UUID;

public interface PaymentRefundService {
    void refundStarPayment(UUID paymentId);
    void resolveRefund(UUID paymentId, boolean refundConfirmed);
}
