package ru.murad.yourmarket.exception;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) { super(message); }
}
