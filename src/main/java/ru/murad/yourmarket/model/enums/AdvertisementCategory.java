package ru.murad.yourmarket.model.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Stable business codes; presentation values are deliberately kept separate from callback codes. */
@Getter
@RequiredArgsConstructor
public enum AdvertisementCategory {
    AUTO("🚗", "Авто", "auto"),
    REAL_ESTATE("🏠", "Недвижимость", "real_estate"),
    ELECTRONICS("📱", "Электроника", "electronics"),
    CLOTHING("👕", "Одежда", "clothing"),
    HOME("🛋", "Для дома", "home"),
    SERVICES("🛠", "Работа и услуги", "services"),
    ANIMALS("🐾", "Животные", "animals"),
    HOBBY_SPORT("⚽", "Хобби и спорт", "hobby_sport"),
    OTHER("📦", "Другое", "other");

    private final String emoji;
    private final String displayName;
    private final String hashtag;

    public String displayLabel() {
        return emoji + " " + displayName;
    }

    public static Optional<AdvertisementCategory> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.name().equals(code)).findFirst();
    }
}
