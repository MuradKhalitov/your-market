package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

class TelegramErrorClassifierTest {
    private final TelegramErrorClassifier classifier = new TelegramErrorClassifier();

    @Test
    void rateLimitIsTransientAndBoundedCategory() {
        TelegramApiRequestException exception = mock(TelegramApiRequestException.class);
        when(exception.getErrorCode()).thenReturn(429);
        assertEquals("429", classifier.classify(exception));
    }

    @Test
    void forbiddenIsPermanentCategory() {
        TelegramApiRequestException exception = mock(TelegramApiRequestException.class);
        when(exception.getErrorCode()).thenReturn(403);
        assertEquals("403", classifier.classify(exception));
        org.junit.jupiter.api.Assertions.assertTrue(classifier.isConfirmedPermanent(exception));
    }

    @Test
    void timeoutIsNotTreatedAsConfirmedFailure() {
        assertEquals("timeout", classifier.classify(new SocketTimeoutException()));
    }
}
