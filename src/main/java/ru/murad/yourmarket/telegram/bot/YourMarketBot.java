package ru.murad.yourmarket.telegram.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.telegram.handler.TelegramUpdateHandler;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class YourMarketBot implements SpringLongPollingBot, LongPollingUpdateConsumer {
    private final TelegramProperties properties;
    private final TelegramUpdateHandler handler;

    @Override public String getBotToken() { return properties.bot().token(); }
    @Override public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }
    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            if (!isSupported(update)) {
                log.debug("Пропущен неподдерживаемый Telegram updateId={}, type={}",
                        update == null ? null : update.getUpdateId(), eventType(update));
                continue;
            }
            try {
                handler.handle(update);
            } catch (RuntimeException ex) {
                log.error("Ошибка обработки Telegram updateId={}; остальные события batch будут продолжены",
                        update.getUpdateId(), ex);
            }
        }
    }

    private boolean isSupported(Update update) {
        return update != null && (update.hasMessage() || update.hasCallbackQuery() || update.hasPreCheckoutQuery());
    }

    private String eventType(Update update) {
        if (update == null) return "null";
        if (update.hasChannelPost()) return "channel_post";
        if (update.hasEditedChannelPost()) return "edited_channel_post";
        return "unsupported";
    }
}
