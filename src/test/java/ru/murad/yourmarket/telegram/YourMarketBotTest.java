package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.telegram.bot.YourMarketBot;
import ru.murad.yourmarket.telegram.handler.TelegramUpdateHandler;
import ru.murad.yourmarket.service.OperationalMetrics;

class YourMarketBotTest {
    private final TelegramUpdateHandler handler = mock(TelegramUpdateHandler.class);
    private final YourMarketBot bot = new YourMarketBot(properties(), handler, mock(OperationalMetrics.class));

    @Test
    void messageBatchIsDelivered() {
        Update message = messageUpdate(1);
        bot.consume(List.of(message));
        verify(handler).handle(message);
    }

    @Test
    void channelPostDoesNotBlockFollowingMessage() {
        Update channelPost = mock(Update.class);
        org.mockito.Mockito.when(channelPost.getUpdateId()).thenReturn(1);
        org.mockito.Mockito.when(channelPost.hasChannelPost()).thenReturn(true);
        Update message = messageUpdate(2);

        bot.consume(List.of(channelPost, message));

        verify(handler, never()).handle(channelPost);
        verify(handler).handle(message);
    }

    @Test
    void failureOfFirstUpdateDoesNotBlockSecond() {
        Update first = messageUpdate(1);
        Update second = messageUpdate(2);
        doThrow(new IllegalStateException("broken update")).when(handler).handle(first);

        bot.consume(List.of(first, second));

        verify(handler).handle(second);
    }

    @Test
    void callbackIsDelivered() {
        Update callback = mock(Update.class);
        org.mockito.Mockito.when(callback.hasCallbackQuery()).thenReturn(true);
        org.mockito.Mockito.when(callback.getCallbackQuery()).thenReturn(mock(CallbackQuery.class));
        bot.consume(List.of(callback));
        verify(handler).handle(callback);
    }

    @Test
    void preCheckoutIsDelivered() {
        Update preCheckout = mock(Update.class);
        org.mockito.Mockito.when(preCheckout.hasPreCheckoutQuery()).thenReturn(true);
        org.mockito.Mockito.when(preCheckout.getPreCheckoutQuery()).thenReturn(mock(PreCheckoutQuery.class));
        bot.consume(List.of(preCheckout));
        verify(handler).handle(preCheckout);
    }

    @Test
    void contextContainsOneBotAndItsWorkingConsumer() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TelegramProperties.class, YourMarketBotTest::properties);
            context.registerBean(TelegramUpdateHandler.class, () -> handler);
            context.registerBean(io.micrometer.core.instrument.MeterRegistry.class,
                    io.micrometer.core.instrument.simple.SimpleMeterRegistry::new);
            context.registerBean(OperationalMetrics.class);
            context.register(YourMarketBot.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(SpringLongPollingBot.class).size());
            assertEquals(1, context.getBeansOfType(LongPollingUpdateConsumer.class).size());
            YourMarketBot registered = context.getBean(YourMarketBot.class);
            assertSame(registered, registered.getUpdatesConsumer());
            Update update = messageUpdate(3);
            registered.getUpdatesConsumer().consume(List.of(update));
            verify(handler).handle(update);
        }
    }

    private Update messageUpdate(int id) {
        Update update = mock(Update.class);
        org.mockito.Mockito.when(update.getUpdateId()).thenReturn(id);
        org.mockito.Mockito.when(update.hasMessage()).thenReturn(true);
        org.mockito.Mockito.when(update.getMessage()).thenReturn(mock(Message.class));
        return update;
    }

    private static TelegramProperties properties() {
        TelegramProperties properties = new TelegramProperties();
        properties.getBot().setToken("token");
        return properties;
    }
}
