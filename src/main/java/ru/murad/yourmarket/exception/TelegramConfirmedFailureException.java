package ru.murad.yourmarket.exception;
public class TelegramConfirmedFailureException extends TelegramPublicationException {
    public TelegramConfirmedFailureException(String message, Throwable cause) { super(message, cause); }
}
