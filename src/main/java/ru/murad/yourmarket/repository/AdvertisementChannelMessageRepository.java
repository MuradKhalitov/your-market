package ru.murad.yourmarket.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.murad.yourmarket.model.AdvertisementChannelMessage;
import java.util.*;
public interface AdvertisementChannelMessageRepository extends JpaRepository<AdvertisementChannelMessage,UUID>{
 List<AdvertisementChannelMessage> findByAdvertisementIdOrderByPosition(UUID advertisementId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select m from AdvertisementChannelMessage m where m.id=:id")
 Optional<AdvertisementChannelMessage> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
 long countByAdvertisementIdAndDeletionStatusNot(UUID advertisementId, ru.murad.yourmarket.model.enums.ChannelMessageStatus status);
 boolean existsByAdvertisementIdAndDeletionStatus(UUID advertisementId,ru.murad.yourmarket.model.enums.ChannelMessageStatus status);
}
