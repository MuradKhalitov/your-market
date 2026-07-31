package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.exception.*;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.*;
import ru.murad.yourmarket.service.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicationRetryServiceImpl implements PublicationRetryService {
    private final AdvertisementRepository advertisements;
    private final PaymentRepository payments;
    private final TelegramUserRepository users;
    private final AdvertisementPublicationService publicationService;

    @Override
    public AdvertisementResponseDto retryForUser(UUID id, Long userId) {
        validateUserRetry(id, userId);
        return publicationService.publish(id);
    }

    @Override
    public AdvertisementResponseDto retryAsAdmin(UUID id) {
        return publicationService.publish(id);
    }

    void validateUserRetry(UUID id, Long userId) {
        Advertisement ad = advertisements.findById(id).orElseThrow(AdvertisementNotFoundException::new);
        if (!ad.getTelegramUserId().equals(userId)) throw new AdvertisementNotFoundException();
        if (ad.getStatus() == AdvertisementStatus.PUBLISHED) return;
        if (ad.getStatus() != AdvertisementStatus.PUBLICATION_FAILED && ad.getStatus() != AdvertisementStatus.PAID)
            throw new InvalidAdvertisementStateException("Объявление нельзя опубликовать повторно.");
        if (ad.getChannelMessageId() != null) throw new InvalidAdvertisementStateException("Объявление уже имеет публикацию.");
        Payment payment = payments.findByAdvertisementId(id).orElseThrow(PaymentNotFoundException::new);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED)
            throw new InvalidPaymentStateException("Публикация доступна только после успешной оплаты.");
        TelegramUser user = users.findByTelegramUserId(userId)
                .orElseThrow(() -> new InvalidAdvertisementStateException("Пользователь не найден."));
        if (user.isBlocked()) throw new InvalidAdvertisementStateException("Пользователь заблокирован.");
    }
}
