package ru.murad.yourmarket.mapper;

import org.mapstruct.*;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.model.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdvertisementMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", source = "username")
    @Mapping(target = "status", constant = "WAITING_FOR_PAYMENT")
    @Mapping(target = "channelMessageId", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "expiredAt", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "publicationOperationId", ignore = true)
    @Mapping(target = "publicationStartedAt", ignore = true)
    @Mapping(target = "publicationFailureReason", ignore = true)
    @Mapping(target = "moderationMessageId", ignore = true)
    @Mapping(target = "moderationSubmittedAt", ignore = true)
    @Mapping(target = "moderationSubmissionStatus", constant = "NOT_SUBMITTED")
    @Mapping(target = "moderationSendingSince", ignore = true)
    @Mapping(target = "publicationPhase", ignore = true)
    @Mapping(target = "publicationUpdatedAt", ignore = true)
    @Mapping(target = "moderationOperationId", ignore = true)
    @Mapping(target = "moderationPhase", ignore = true)
    @Mapping(target = "expirationOperationId", ignore = true)
    @Mapping(target = "expirationStartedAt", ignore = true)
    Advertisement toAdvertisement(AdvertisementDraft draft, String username);

    AdvertisementResponseDto toResponse(Advertisement advertisement);
}
