package ru.murad.yourmarket.service;

import java.util.UUID;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementDraft;

public interface VehicleDetailsService {
    void copyToAdvertisement(AdvertisementDraft draft, Advertisement advertisement);
    String formatForDisplay(UUID advertisementId);
}
