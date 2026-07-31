package ru.murad.yourmarket.service;

import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import java.util.*;

public interface AdvertisementService {
    List<AdvertisementResponseDto> findRecentForUser(Long telegramUserId);
    AdvertisementResponseDto deletePublished(UUID id, Long telegramUserId);
    AdvertisementResponseDto findById(UUID id);
}
