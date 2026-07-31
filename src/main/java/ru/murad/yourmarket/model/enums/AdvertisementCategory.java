package ru.murad.yourmarket.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdvertisementCategory {
    ELECTRONICS("📱", "Электроника", "electronics"),
    AUTO("🚗", "Авто", "auto"),
    HOME("🏠", "Дом и быт", "home"),
    CLOTHES("👕", "Одежда", "clothes"),
    GAMES("🎮", "Игры", "games"),
    OTHER("📦", "Другое", "other");

    private final String emoji;
    private final String displayName;
    private final String hashtag;
}
