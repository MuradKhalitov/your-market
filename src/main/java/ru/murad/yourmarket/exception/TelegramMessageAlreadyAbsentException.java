package ru.murad.yourmarket.exception;
public class TelegramMessageAlreadyAbsentException extends RuntimeException {
    public TelegramMessageAlreadyAbsentException(String message, Throwable cause) { super(message, cause); }
}
