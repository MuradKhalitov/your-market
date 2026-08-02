package ru.murad.yourmarket.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransmissionType {
    AUTOMATIC("Автомат"), MANUAL("Механика"), CVT("Вариатор"), ROBOT("Робот");
    private final String displayName;
}
