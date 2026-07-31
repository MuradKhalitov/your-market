package ru.murad.yourmarket.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.TelegramChannelLinkService;

@Service
@RequiredArgsConstructor
public class TelegramChannelLinkServiceImpl implements TelegramChannelLinkService {
    private final TelegramProperties properties;

    @Override
    public String messageUrl(Integer messageId) {
        String username = properties.channel().username();
        if (messageId == null || username == null || username.isBlank()) return null;
        String normalized = username.trim().replaceFirst("^@", "");
        return normalized.isBlank() ? null : "https://t.me/" + normalized + "/" + messageId;
    }
}
