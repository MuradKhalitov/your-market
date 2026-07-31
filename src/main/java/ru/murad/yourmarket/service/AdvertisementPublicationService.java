package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import java.util.UUID;

public interface AdvertisementPublicationService {
    AdvertisementResponseDto publish(UUID advertisementId);
    AdvertisementResponseDto publishFromModeration(UUID advertisementId);
}
