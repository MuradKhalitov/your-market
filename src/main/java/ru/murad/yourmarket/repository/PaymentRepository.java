package ru.murad.yourmarket.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.murad.yourmarket.model.Payment;
import java.util.*;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPayload(String payload);
    List<Payment> findByAdvertisementIdOrderByCreatedAtDesc(UUID advertisementId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.advertisementId = :advertisementId order by p.createdAt desc")
    List<Payment> findByAdvertisementIdForUpdate(@Param("advertisementId") UUID advertisementId);
    default Optional<Payment> findByAdvertisementId(UUID advertisementId) {
        return findByAdvertisementIdOrderByCreatedAtDesc(advertisementId).stream().findFirst();
    }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.payload = :payload")
    Optional<Payment> findByPayloadForUpdate(@Param("payload") String payload);
}
