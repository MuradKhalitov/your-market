package ru.murad.yourmarket.service;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import java.util.UUID;
public interface ModerationService {
    Integer submit(UUID advertisementId);
    AdvertisementResponseDto approve(UUID advertisementId, Long adminUserId);
    AdvertisementResponseDto reject(UUID advertisementId, Long adminUserId, String reason);
}
