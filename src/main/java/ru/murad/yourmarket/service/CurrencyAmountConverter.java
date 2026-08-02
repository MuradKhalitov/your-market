package ru.murad.yourmarket.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class CurrencyAmountConverter {
    public int toMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        try {
            return switch (currency) {
                case "RUB" -> amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).intValueExact();
                case "XTR" -> amount.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
                default -> throw new IllegalArgumentException("Unsupported payment currency: " + currency);
            };
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Payment amount has an invalid scale or does not fit Telegram amount type",
                    exception);
        }
    }
}
