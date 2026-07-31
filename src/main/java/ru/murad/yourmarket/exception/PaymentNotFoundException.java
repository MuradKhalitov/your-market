package ru.murad.yourmarket.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() { super("Платёж не найден"); }
}
