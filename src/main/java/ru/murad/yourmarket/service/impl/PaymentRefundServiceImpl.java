package ru.murad.yourmarket.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.service.PaymentRefundService;
import ru.murad.yourmarket.service.PaymentRefundTransactionService;
import ru.murad.yourmarket.telegram.TelegramGateway;
import ru.murad.yourmarket.exception.TelegramConfirmedFailureException;

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
        } catch (TelegramConfirmedFailureException exception) {
            try {
                transactions.release(paymentId, claim.operationId());
            } catch (RuntimeException releaseError) {
                log.error("Cannot release Telegram Stars refund claim paymentId={}", paymentId, releaseError);
            }
            throw exception;
        } catch (RuntimeException exception) {
            try {
                transactions.markReconciliationRequired(paymentId, claim.operationId(), exception.getMessage());
            } catch (RuntimeException markError) {
                log.error("Cannot mark Telegram Stars refund reconciliation paymentId={}", paymentId, markError);
            }
            throw exception;
        }
    }

    @Override
    public void resolveRefund(UUID paymentId, boolean refundConfirmed) {
        transactions.resolve(paymentId, refundConfirmed);
        log.info("Telegram Stars refund reconciliation resolved paymentId={}, refundConfirmed={}",
                paymentId, refundConfirmed);
    }
}
