package ru.murad.yourmarket.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.murad.yourmarket.model.TelegramUser;
import java.util.Optional;
import java.util.UUID;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, UUID> {
    Optional<TelegramUser> findByTelegramUserId(Long telegramUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TelegramUser u where u.telegramUserId = :telegramUserId")
    Optional<TelegramUser> findByTelegramUserIdForUpdate(@Param("telegramUserId") Long telegramUserId);
}
