package ru.murad.yourmarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A timeout cannot prove whether Telegram completed a refund. Stale claims are
 * therefore made visible for protected administrator reconciliation, not retried.
 */
@Component
@RequiredArgsConstructor
public class PaymentRefundRecoveryScheduler {
    private final PaymentRefundTransactionService transactions;

    @Scheduled(fixedDelayString = "${publication.refund-recovery-delay-ms:60000}")
    public void recoverStaleClaims() {
        transactions.recoverStaleClaims();
    }
}
