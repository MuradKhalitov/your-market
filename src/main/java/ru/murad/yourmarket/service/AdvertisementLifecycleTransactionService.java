package ru.murad.yourmarket.service;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;import ru.murad.yourmarket.model.Advertisement;import java.util.UUID;
public interface AdvertisementLifecycleTransactionService {Advertisement authorizeDeletion(UUID id,Long userId);ExpirationClaim claimExpiration(UUID id);AdvertisementResponseDto finishDeletion(UUID id);Advertisement finishExpiration(UUID id,UUID operationId);void releaseExpiration(UUID id,UUID operationId);boolean recoverStaleExpiration(UUID id);record ExpirationClaim(Advertisement advertisement,UUID operationId){}}
