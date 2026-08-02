package ru.murad.yourmarket.service.impl;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.exception.InvalidAdvertisementStateException;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.VehicleDetails;
import ru.murad.yourmarket.model.VehicleDraftDetails;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.model.enums.EngineType;
import ru.murad.yourmarket.repository.VehicleDetailsRepository;
import ru.murad.yourmarket.repository.VehicleDraftDetailsRepository;
import ru.murad.yourmarket.service.VehicleDetailsService;
import ru.murad.yourmarket.telegram.TelegramGatewayImpl;

@Service
@RequiredArgsConstructor
public class VehicleDetailsServiceImpl implements VehicleDetailsService {
    private final VehicleDraftDetailsRepository draftDetails;
    private final VehicleDetailsRepository details;
    private final ru.murad.yourmarket.service.VehicleDetailsFormatter formatter;

    @Override
    @Transactional
    public void copyToAdvertisement(AdvertisementDraft draft, Advertisement advertisement) {
        if (draft.getCategory() != AdvertisementCategory.AUTO) return;
        if (details.findByAdvertisementId(advertisement.getId()).isPresent()) return;
        VehicleDraftDetails source = draftDetails.findByAdvertisementDraftId(draft.getId())
                .orElseThrow(() -> new InvalidAdvertisementStateException("Заполните характеристики автомобиля перед оплатой."));
        validate(source);
        details.save(VehicleDetails.builder()
                .advertisementId(advertisement.getId())
                .brandCode(source.getBrandCode()).brandNameSnapshot(source.getBrandNameSnapshot()).customBrand(source.getCustomBrand())
                .modelCode(source.getModelCode()).modelNameSnapshot(source.getModelNameSnapshot()).customModel(source.getCustomModel())
                .productionYear(source.getProductionYear()).transmission(source.getTransmission()).engineType(source.getEngineType())
                .engineVolumeLiters(source.getEngineVolumeLiters()).mileageKm(source.getMileageKm()).driveType(source.getDriveType()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public String formatForDisplay(UUID advertisementId) {
        return details.findByAdvertisementId(advertisementId).map(formatter::format).orElse("");
    }

    private void validate(VehicleDraftDetails source) {
        if (blank(source.getBrandCode()) || blank(source.getBrandNameSnapshot()) || blank(source.getModelCode())
                || blank(source.getModelNameSnapshot()) || source.getProductionYear() == null || source.getTransmission() == null
                || source.getEngineType() == null || source.getMileageKm() == null || source.getDriveType() == null
                || (source.getEngineType() != EngineType.ELECTRIC && source.getEngineVolumeLiters() == null))
            throw new InvalidAdvertisementStateException("Заполните все характеристики автомобиля перед оплатой.");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
