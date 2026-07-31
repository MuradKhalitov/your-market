package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import java.util.UUID;

public interface PublicationRetryService {
    AdvertisementResponseDto retryForUser(UUID advertisementId, Long telegramUserId);
    AdvertisementResponseDto retryAsAdmin(UUID advertisementId);
}
