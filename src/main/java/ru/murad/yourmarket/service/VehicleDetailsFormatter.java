package ru.murad.yourmarket.service;

import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.murad.yourmarket.model.VehicleDetails;
import ru.murad.yourmarket.model.VehicleDraftDetails;
import ru.murad.yourmarket.model.enums.EngineType;
import ru.murad.yourmarket.telegram.TelegramGatewayImpl;

@Component
public class VehicleDetailsFormatter {
    public String format(VehicleDetails value) {
        return format(value.getBrandNameSnapshot(), value.getModelNameSnapshot(), value.getProductionYear(),
                value.getTransmission().getDisplayName(), value.getEngineType(), value.getEngineVolumeLiters(),
                value.getMileageKm(), value.getDriveType().getDisplayName());
    }
    public String format(VehicleDraftDetails value) {
        return format(value.getBrandNameSnapshot(), value.getModelNameSnapshot(), value.getProductionYear(),
                value.getTransmission().getDisplayName(), value.getEngineType(), value.getEngineVolumeLiters(),
                value.getMileageKm(), value.getDriveType().getDisplayName());
    }
    private String format(String brand, String model, Integer year, String transmission, EngineType engine,
                          java.math.BigDecimal volume, Integer mileage, String drive) {
        if (brand == null || model == null || year == null || transmission == null || engine == null || mileage == null || drive == null) return "";
        if (engine != EngineType.ELECTRIC && volume == null) return "";
        String engineLine = engine == EngineType.ELECTRIC ? "⚡ Двигатель: " + engine.getDisplayName()
                : "⛽ Двигатель: " + engine.getDisplayName() + ", " + volume.toPlainString() + " л";
        return "🚗 " + TelegramGatewayImpl.html(brand) + " " + TelegramGatewayImpl.html(model)
                + "\n📅 Год: " + year + "\n⚙️ Коробка: " + transmission + "\n" + engineLine
                + "\n🛣 Пробег: " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(mileage)
                + " км\n🚙 Привод: " + drive;
    }
}
