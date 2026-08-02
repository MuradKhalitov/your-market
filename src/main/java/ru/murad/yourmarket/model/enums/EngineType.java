package ru.murad.yourmarket.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EngineType {
    PETROL("Бензин"), DIESEL("Дизель"), LPG("Газ"), HYBRID("Гибрид"), ELECTRIC("Электрический");
    private final String displayName;
}
