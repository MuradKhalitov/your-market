package ru.murad.yourmarket.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import java.util.*;
import java.time.Instant;

public interface AdvertisementRepository extends JpaRepository<Advertisement, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Advertisement a where a.id = :id")
    Optional<Advertisement> findByIdForUpdate(@Param("id") UUID id);

    List<Advertisement> findByTelegramUserIdOrderByCreatedAtDesc(Long telegramUserId, Pageable pageable);
    Optional<Advertisement> findFirstByTelegramUserIdAndStatusOrderByCreatedAtDesc(
            Long telegramUserId, AdvertisementStatus status);
    List<Advertisement> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            AdvertisementStatus status, Instant expiresAt);
    List<Advertisement> findTop100ByStatusAndPublicationUpdatedAtLessThanEqualOrderByPublicationUpdatedAtAsc(AdvertisementStatus status,Instant time);
    List<Advertisement> findTop100ByStatusAndExpirationStartedAtLessThanEqualOrderByExpirationStartedAtAsc(AdvertisementStatus status,Instant time);
}
