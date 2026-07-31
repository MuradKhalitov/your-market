package ru.murad.yourmarket.telegram.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.telegram.handler.TelegramUpdateHandler;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YourMarketBot implements SpringLongPollingBot, LongPollingUpdateConsumer {
    private final TelegramProperties properties;
    private final TelegramUpdateHandler handler;

    @Override public String getBotToken() { return properties.bot().token(); }
    @Override public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }
    @Override public void consume(List<Update> updates) { updates.forEach(handler::handle); }
}
