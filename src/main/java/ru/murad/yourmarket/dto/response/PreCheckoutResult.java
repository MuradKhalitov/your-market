package ru.murad.yourmarket.dto.response;

public record PreCheckoutResult(boolean approved, String errorMessage) {
    public static PreCheckoutResult approve() { return new PreCheckoutResult(true, null); }
    public static PreCheckoutResult reject(String message) { return new PreCheckoutResult(false, message); }
}
