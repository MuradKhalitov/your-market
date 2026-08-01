package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.impl.TelegramBotLinkServiceImpl;

class TelegramBotLinkServiceTest {

    @Test
    void buildsLinkForUsernameWithAt() {
        assertEquals("https://t.me/your_market_bot?start=publish", service("@your_market_bot").buildPublishAdvertisementLink());
    }

    @Test
    void buildsLinkForUsernameWithoutAt() {
        assertEquals("https://t.me/your_market_bot?start=publish", service("your_market_bot").buildPublishAdvertisementLink());
    }

    private TelegramBotLinkService service(String username) {
        TelegramProperties properties = new TelegramProperties();
        properties.getBot().setUsername(username);
        return new TelegramBotLinkServiceImpl(properties);
    }
}
