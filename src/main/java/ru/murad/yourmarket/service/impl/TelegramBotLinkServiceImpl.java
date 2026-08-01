package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.TelegramBotLinkService;

@Service
@RequiredArgsConstructor
public class TelegramBotLinkServiceImpl implements TelegramBotLinkService {
    private final TelegramProperties telegram;

    @Override
    public String buildPublishAdvertisementLink() {
        String username = telegram.getBot().getUsername().trim();
        if (username.startsWith("@")) username = username.substring(1);
        return "https://t.me/" + username + "?start=publish";
    }
}
