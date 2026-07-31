package ru.murad.yourmarket.service;

import ru.murad.yourmarket.model.TelegramUser;

public interface TelegramUserService {
    TelegramUser registerOrUpdate(Long telegramUserId, Long chatId, String username, String firstName);
}
