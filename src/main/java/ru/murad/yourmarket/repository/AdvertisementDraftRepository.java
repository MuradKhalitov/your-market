package ru.murad.yourmarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.murad.yourmarket.model.AdvertisementDraft;
import java.util.Optional;
import java.util.UUID;

public interface AdvertisementDraftRepository extends JpaRepository<AdvertisementDraft, UUID> {
    Optional<AdvertisementDraft> findByTelegramUserId(Long telegramUserId);
    void deleteByTelegramUserId(Long telegramUserId);
}
