package ru.murad.yourmarket.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DriveType {
    FRONT("Передний"), REAR("Задний"), AWD("Полный");
    private final String displayName;
}
