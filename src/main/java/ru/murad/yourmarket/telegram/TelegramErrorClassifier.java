package ru.murad.yourmarket.telegram;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/** Maps Telegram failures to bounded operational categories, never to user-controlled text. */
@Component
public class TelegramErrorClassifier {
    public String classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TelegramApiRequestException request) {
                Integer code = request.getErrorCode();
                if (Integer.valueOf(400).equals(code)) return "400";
                if (Integer.valueOf(403).equals(code)) return "403";
                if (Integer.valueOf(429).equals(code)) return "429";
                if (code != null && code >= 500) return "5xx";
                return "other";
            }
            if (current instanceof SocketTimeoutException) return "timeout";
            if (current instanceof IOException) return "network";
            current = current.getCause();
        }
        return "other";
    }

    public boolean isConfirmedPermanent(Throwable throwable) {
        String result = classify(throwable);
        return "400".equals(result) || "403".equals(result);
    }
}
