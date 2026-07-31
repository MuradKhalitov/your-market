package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.model.TelegramUser;
import ru.murad.yourmarket.repository.TelegramUserRepository;
import ru.murad.yourmarket.service.TelegramUserService;

@Service
@RequiredArgsConstructor
public class TelegramUserServiceImpl implements TelegramUserService {
    private final TelegramUserRepository repository;

    @Override @Transactional
    public TelegramUser registerOrUpdate(Long userId, Long chatId, String username, String firstName) {
        TelegramUser user = repository.findByTelegramUserId(userId)
                .orElseGet(() -> TelegramUser.builder().telegramUserId(userId).build());
        user.setChatId(chatId);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setBlocked(false);
        return repository.save(user);
    }
}
