package ru.murad.yourmarket.config;

import org.springframework.context.annotation.*;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramClientConfig {
    @Bean
    TelegramClient telegramClient(TelegramProperties properties) {
        return new OkHttpTelegramClient(properties.bot().token());
    }
}
