package ru.murad.yourmarket.exception;

public class AdvertisementNotFoundException extends RuntimeException {
    public AdvertisementNotFoundException() { super("Объявление не найдено"); }
}
