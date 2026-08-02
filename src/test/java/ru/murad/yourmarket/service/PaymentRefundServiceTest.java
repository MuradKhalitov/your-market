package ru.murad.yourmarket.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.service.impl.PaymentRefundServiceImpl;
import ru.murad.yourmarket.telegram.TelegramGateway;

class PaymentRefundServiceTest {
    private final PaymentRefundTransactionService transactions = mock(PaymentRefundTransactionService.class);
    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final PaymentRefundService service = new PaymentRefundServiceImpl(transactions, gateway);

    @Test
    void claimedStarsRefundCallsTelegramAndCompletes() {
        UUID paymentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(transactions.claim(paymentId)).thenReturn(new PaymentRefundTransactionService.RefundClaim(
                paymentId, operationId, 10L, "telegram-charge", true));

        service.refundStarPayment(paymentId);

        verify(gateway).refundStarPayment(10L, "telegram-charge");
        verify(transactions).complete(paymentId, operationId);
    }

    @Test
    void alreadyRefundedPaymentDoesNotCallTelegramAgain() {
        UUID paymentId = UUID.randomUUID();
        when(transactions.claim(paymentId)).thenReturn(new PaymentRefundTransactionService.RefundClaim(
                paymentId, null, 10L, "telegram-charge", false));

        service.refundStarPayment(paymentId);

        verify(gateway, never()).refundStarPayment(10L, "telegram-charge");
    }
}
