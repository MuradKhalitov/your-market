package ru.murad.yourmarket.dto.request;

public record SuccessfulPaymentRequest(Long telegramUserId, String payload, String currency,
                                       Long totalAmount, String telegramChargeId, String providerChargeId) {}
