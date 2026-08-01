package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.enums.AdvertisementCreationStep;
import ru.murad.yourmarket.model.enums.InvoiceSendStatus;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.service.AdvertisementDraftService;
import ru.murad.yourmarket.service.AdvertisementPublicationService;
import ru.murad.yourmarket.service.AdvertisementService;
import ru.murad.yourmarket.service.ModerationService;
import ru.murad.yourmarket.service.PaymentService;
import ru.murad.yourmarket.service.PublicationRetryService;
import ru.murad.yourmarket.service.RateLimitService;
import ru.murad.yourmarket.service.TelegramChannelLinkService;
import ru.murad.yourmarket.service.TelegramUserService;
import ru.murad.yourmarket.telegram.handler.TelegramUpdateHandler;
import ru.murad.yourmarket.telegram.keyboard.TelegramKeyboardFactory;
import ru.murad.yourmarket.telegram.handler.StartCommandParser;

class TelegramUpdateHandlerTest {

    TelegramClient client = mock(TelegramClient.class);
    TelegramGateway gateway = mock(TelegramGateway.class);
    TelegramUserService users = mock(TelegramUserService.class);
    AdvertisementDraftService drafts = mock(AdvertisementDraftService.class);
    PaymentService payments = mock(PaymentService.class);
    AdvertisementPublicationService publications = mock(AdvertisementPublicationService.class);
    AdvertisementService advertisements = mock(AdvertisementService.class);
    PublicationRetryService retries = mock(PublicationRetryService.class);
    TelegramChannelLinkService links = mock(TelegramChannelLinkService.class);
    ModerationService moderation = mock(ModerationService.class);
    RateLimitService rateLimit = mock(RateLimitService.class);
    ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository draftPhotos = mock(
        ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository.class);
    TelegramProperties telegram = new TelegramProperties(new TelegramProperties.Bot("bot", "token"),
        new TelegramProperties.Channel("-1", "channel", "https://t.me/channel"),
        new TelegramProperties.Payment("provider"));
    PublicationProperties publication = publicationProperties();
    TelegramKeyboardFactory keyboards = new TelegramKeyboardFactory(publication);
    StartCommandParser startCommands = new StartCommandParser();

    private PublicationProperties publicationProperties() {
        PublicationProperties properties = new PublicationProperties();
        properties.setPrice(new BigDecimal("199.00"));
        return properties;
    }
    TelegramUpdateHandler handler = new TelegramUpdateHandler(client, gateway, keyboards, telegram,
        publication,
        users, drafts, payments, publications, advertisements, retries, links, moderation,
        rateLimit, draftPhotos, startCommands);

    @BeforeEach
    void clientAcceptsMessages() throws Exception {
        when(client.execute(any(SendMessage.class))).thenReturn(null);
        when(rateLimit.allow(anyLong(), anyString())).thenReturn(true);
    }

    @Test
    void startReturnsPersistentMainMenu() throws Exception {
        handler.handle(update("/start"));
        ReplyKeyboardMarkup keyboard = sentReplyKeyboard();
        assertMainMenu(keyboard);
    }

    @Test
    void publishDeepLinkStartsNewDraft() throws Exception {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_CATEGORY);
        when(drafts.findActive(1L)).thenReturn(Optional.empty());
        when(drafts.startCreation(1L, 10L)).thenReturn(draft);

        handler.handle(update("/start publish"));

        verify(drafts).startCreation(1L, 10L);
        assertTrue(lastMessage().getText().startsWith("Шаг 1 из 7"));
    }

    @Test
    void publishDeepLinkResumesExistingDraft() throws Exception {
        when(drafts.findActive(1L)).thenReturn(Optional.of(draft(AdvertisementCreationStep.WAITING_FOR_PRICE)));

        handler.handle(update("/start publish"));

        verify(drafts, never()).startCreation(anyLong(), anyLong());
        assertTrue(lastMessage().getText().startsWith("Шаг 4 из 7"));
    }

    @Test
    void menuReturnsPersistentMainMenu() throws Exception {
        when(drafts.findActive(1L)).thenReturn(Optional.empty());
        handler.handle(update("/menu"));
        assertMainMenu(sentReplyKeyboard());
    }

    @Test
    void startingCreationShowsCancelKeyboard() throws Exception {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_CATEGORY);
        when(drafts.findActive(1L)).thenReturn(Optional.empty());
        when(drafts.startCreation(1L, 10L)).thenReturn(draft);
        handler.handle(update(TelegramKeyboardFactory.CREATE));
        ReplyKeyboardMarkup keyboard = sentReplyKeyboard();
        assertEquals(List.of(TelegramKeyboardFactory.CANCEL_CREATION), rowTexts(keyboard, 0));
    }

    @Test
    void cancellationDeletesDraftAndReturnsMenu() throws Exception {
        handler.handle(update(TelegramKeyboardFactory.CANCEL_CREATION));
        verify(drafts).cancel(1L);
        ArgumentCaptor<SendMessage> captor = messages();
        assertEquals("Создание объявления отменено", captor.getValue().getText());
        assertMainMenu((ReplyKeyboardMarkup) captor.getValue().getReplyMarkup());
    }

    @Test
    void repeatedStartResumesExistingDraft() throws Exception {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_PRICE);
        when(drafts.findActive(1L)).thenReturn(Optional.of(draft));
        handler.handle(update(TelegramKeyboardFactory.CREATE));
        verify(drafts, never()).startCreation(anyLong(), anyLong());
        assertTrue(lastMessage().getText().startsWith("Шаг 4 из 7"));
    }

    @ParameterizedTest
    @ValueSource(strings = {TelegramKeyboardFactory.MY_ADS, TelegramKeyboardFactory.CHANNEL,
        TelegramKeyboardFactory.RULES})
    void menuButtonsAreRouted(String button) {
        when(advertisements.findRecentForUser(1L)).thenReturn(List.of());
        handler.handle(update(button));
        if (TelegramKeyboardFactory.MY_ADS.equals(button)) {
            verify(advertisements).findRecentForUser(1L);
        } else {
            verify(advertisements, never()).findRecentForUser(anyLong());
        }
    }

    @Test
    void unknownTextDuringCreationIsProcessedByCurrentStep() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_TITLE);
        when(drafts.findActive(1L)).thenReturn(Optional.of(draft));
        when(drafts.setTitle(1L, "Новый товар")).thenReturn(
            draft(AdvertisementCreationStep.WAITING_FOR_DESCRIPTION));
        handler.handle(update("Новый товар"));
        verify(drafts).setTitle(1L, "Новый товар");
    }

    @Test
    void correlationMdcIsClearedAfterUpdate() {
        handler.handle(update("/start"));
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("telegramUserId"));
    }

    @Test
    void repeatedSentInvoiceShowsClearHintWithoutSendingAnotherInvoice() throws Exception {
        Payment payment = payment(InvoiceSendStatus.SENT);
        when(payments.createPaymentAndClaimInvoice(1L, "seller"))
                .thenReturn(new PaymentService.InvoiceClaim(payment, null, PaymentService.InvoiceClaimResult.ALREADY_SENT));

        handler.handle(callback("pay"));

        assertEquals("Счёт уже был отправлен в этот чат выше. Найдите сообщение с кнопкой „Оплатить“. Новый счёт не создавался.",
                lastMessage().getText());
        verify(gateway, never()).sendInvoice(anyLong(), any(Payment.class));
        verify(payments, never()).markInvoiceSent(any(), any());
        assertEquals(InvoiceSendStatus.SENT, payment.getInvoiceSendStatus());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
    }

    @Test
    void unknownInvoiceShowsSeparateWarningWithoutSendingAnotherInvoice() throws Exception {
        Payment payment = payment(InvoiceSendStatus.SEND_UNKNOWN);
        when(payments.createPaymentAndClaimInvoice(1L, "seller"))
                .thenReturn(new PaymentService.InvoiceClaim(payment, null, PaymentService.InvoiceClaimResult.UNKNOWN));

        handler.handle(callback("pay"));

        assertEquals("Не удалось однозначно определить, был ли счёт отправлен. Новый платёж не создавался. Проверьте сообщения выше или обратитесь к администратору.",
                lastMessage().getText());
        verify(gateway, never()).sendInvoice(anyLong(), any(Payment.class));
        verify(payments, never()).markInvoiceUnknown(any(), any());
        assertEquals(InvoiceSendStatus.SEND_UNKNOWN, payment.getInvoiceSendStatus());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
    }

    @Test
    void invoiceInProgressDoesNotSendAnotherInvoice() throws Exception {
        Payment payment = payment(InvoiceSendStatus.SENDING);
        java.util.UUID operationId = java.util.UUID.randomUUID();
        payment.setInvoiceOperationId(operationId);
        when(payments.createPaymentAndClaimInvoice(1L, "seller"))
                .thenReturn(new PaymentService.InvoiceClaim(payment, operationId,
                        PaymentService.InvoiceClaimResult.IN_PROGRESS));

        handler.handle(callback("pay"));

        assertEquals("Счёт уже формируется. Подождите несколько секунд и проверьте сообщения в этом чате.",
                lastMessage().getText());
        verify(gateway, never()).sendInvoice(anyLong(), any(Payment.class));
        assertEquals(InvoiceSendStatus.SENDING, payment.getInvoiceSendStatus());
        assertEquals(operationId, payment.getInvoiceOperationId());
    }

    @Test
    void concurrentPayCallbacksSendExactlyOneInvoice() throws Exception {
        Payment payment = payment(InvoiceSendStatus.SENDING);
        java.util.UUID operationId = java.util.UUID.randomUUID();
        payment.setInvoiceOperationId(operationId);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(payments.createPaymentAndClaimInvoice(1L, "seller")).thenAnswer(invocation ->
                calls.getAndIncrement() == 0
                        ? new PaymentService.InvoiceClaim(payment, operationId, PaymentService.InvoiceClaimResult.CLAIMED)
                        : new PaymentService.InvoiceClaim(payment, operationId, PaymentService.InvoiceClaimResult.IN_PROGRESS));

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = List.of((java.util.concurrent.Callable<Void>) () -> {
                start.await(); handler.handle(callback("pay")); return null;
            }, (java.util.concurrent.Callable<Void>) () -> {
                start.await(); handler.handle(callback("pay")); return null;
            });
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) future.get();
        }

        verify(gateway, times(1)).sendInvoice(10L, payment);
        verify(payments, times(1)).markInvoiceSent(payment.getId(), operationId);
    }

    private Update update(String text) {
        User user = User.builder().id(1L).userName("seller").firstName("Иван").isBot(false).build();
        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getChatId()).thenReturn(10L);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    private Update callback(String data) {
        User user = User.builder().id(1L).userName("seller").firstName("Иван").isBot(false).build();
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(10L);
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.getId()).thenReturn("callback-id");
        when(callback.getFrom()).thenReturn(user);
        when(callback.getMessage()).thenReturn(message);
        when(callback.getData()).thenReturn(data);
        Update update = mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callback);
        return update;
    }

    private Payment payment(InvoiceSendStatus status) {
        return Payment.builder().id(java.util.UUID.randomUUID()).advertisementId(java.util.UUID.randomUUID())
                .telegramUserId(1L).status(PaymentStatus.CREATED).invoiceSendStatus(status).build();
    }

    private AdvertisementDraft draft(AdvertisementCreationStep step) {
        return AdvertisementDraft.builder().telegramUserId(1L).chatId(10L).step(step).build();
    }

    private SendMessage lastMessage() throws Exception {
        return messages().getValue();
    }

    private ReplyKeyboardMarkup sentReplyKeyboard() throws Exception {
        return (ReplyKeyboardMarkup) lastMessage().getReplyMarkup();
    }

    private ArgumentCaptor<SendMessage> messages() throws Exception {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, atLeastOnce()).execute(captor.capture());
        return captor;
    }

    private void assertMainMenu(ReplyKeyboardMarkup keyboard) {
        assertTrue(keyboard.getResizeKeyboard());
        assertTrue(keyboard.getIsPersistent());
        assertFalse(keyboard.getOneTimeKeyboard());
        assertEquals("Выберите действие", keyboard.getInputFieldPlaceholder());
        assertEquals(List.of(TelegramKeyboardFactory.CREATE, TelegramKeyboardFactory.MY_ADS),
            rowTexts(keyboard, 0));
        assertEquals(List.of(TelegramKeyboardFactory.CHANNEL, TelegramKeyboardFactory.RULES),
            rowTexts(keyboard, 1));
    }

    private List<String> rowTexts(ReplyKeyboardMarkup keyboard, int row) {
        return keyboard.getKeyboard().get(row).stream().map(button -> button.getText()).toList();
    }
}
