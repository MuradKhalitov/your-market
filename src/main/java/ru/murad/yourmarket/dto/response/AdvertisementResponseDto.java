package ru.murad.yourmarket.dto.response;

import ru.murad.yourmarket.model.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdvertisementResponseDto(
        UUID id, Long telegramUserId, Long chatId, AdvertisementCategory category, String title, String description,
        BigDecimal itemPrice, String city, String contact, AdvertisementStatus status,
        Integer channelMessageId, Instant createdAt, Instant paidAt, Instant publishedAt,
        Instant expiresAt, Instant expiredAt, Instant rejectedAt, String rejectionReason) {}
