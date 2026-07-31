package ru.murad.yourmarket.exception;
public class TelegramMessageDeletionException extends RuntimeException {
    public TelegramMessageDeletionException(String message, Throwable cause) { super(message, cause); }
}
