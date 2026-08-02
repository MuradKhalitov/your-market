package ru.murad.yourmarket.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.service.PaymentRefundService;
import ru.murad.yourmarket.service.PaymentRefundTransactionService;
import ru.murad.yourmarket.telegram.TelegramGateway;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {
    private final PaymentRefundTransactionService transactions;
    private final TelegramGateway telegramGateway;

    @Override
    public void refundStarPayment(UUID paymentId) {
        PaymentRefundTransactionService.RefundClaim claim = transactions.claim(paymentId);
        if (!claim.claimed()) return;
        try {
            telegramGateway.refundStarPayment(claim.telegramUserId(), claim.telegramPaymentChargeId());
            transactions.complete(paymentId, claim.operationId());
            log.info("Telegram Stars refund completed paymentId={}", paymentId);
        } catch (RuntimeException exception) {
            try {
                transactions.release(paymentId, claim.operationId());
            } catch (RuntimeException releaseError) {
                log.error("Cannot release Telegram Stars refund claim paymentId={}", paymentId, releaseError);
            }
            throw exception;
        }
    }
}
