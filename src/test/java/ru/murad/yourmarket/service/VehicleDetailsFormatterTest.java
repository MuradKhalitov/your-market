package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.model.VehicleDetails;
import ru.murad.yourmarket.model.enums.*;

class VehicleDetailsFormatterTest {
    private final VehicleDetailsFormatter formatter = new VehicleDetailsFormatter();
    @Test void formatsCombustionVehicleAndEscapesSnapshot() {
        String text = formatter.format(vehicle(EngineType.PETROL, new BigDecimal("2.0"), "Toyota <x>", "Camry"));
        assertTrue(text.contains("Toyota &lt;x&gt; Camry")); assertTrue(text.contains("2.0 л")); assertTrue(text.contains("85 000") || text.contains("85 000"));
    }
    @Test void electricDoesNotPrintVolume() {
        String text = formatter.format(vehicle(EngineType.ELECTRIC, null, "Tesla", "Model 3"));
        assertTrue(text.contains("⚡ Двигатель: Электрический")); assertFalse(text.contains(", "));
    }
    private VehicleDetails vehicle(EngineType engine, BigDecimal volume, String brand, String model) {
        return VehicleDetails.builder().brandCode("X").brandNameSnapshot(brand).modelCode("Y").modelNameSnapshot(model)
                .productionYear(2022).transmission(TransmissionType.AUTOMATIC).engineType(engine).engineVolumeLiters(volume)
                .mileageKm(85000).driveType(DriveType.AWD).build();
    }
}
