package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.payments.RefundStarPayment;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementPhoto;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.telegram.keyboard.TelegramKeyboardFactory;
import ru.murad.yourmarket.service.OperationalMetrics;

class TelegramPublicationKeyboardTest {
    private final TelegramClient client = mock(TelegramClient.class);
    private final AdvertisementPhotoRepository photos = mock(AdvertisementPhotoRepository.class);
    private final TelegramGatewayImpl gateway = new TelegramGatewayImpl(client, properties(),
            new TelegramKeyboardFactory(new PublicationProperties()), photos, mock(OperationalMetrics.class),
            new TelegramErrorClassifier());

    @Test
    void starsInvoiceUsesStarsWithoutMinorUnitConversion() throws Exception {
        Payment payment = Payment.builder().payload("opaque-payload").amount(1)
                .currency("XTR").build();

        gateway.sendInvoice(10L, payment);

        ArgumentCaptor<SendInvoice> captor = ArgumentCaptor.forClass(SendInvoice.class);
        verify(client).execute(captor.capture());
        assertEquals("XTR", captor.getValue().getCurrency());
        assertEquals(1, captor.getValue().getPrices().getFirst().getAmount());
        assertEquals(1, captor.getValue().getPrices().size());
        assertNull(captor.getValue().getMaxTipAmount());
        assertTrue(captor.getValue().getSuggestedTipAmounts() == null
                || captor.getValue().getSuggestedTipAmounts().isEmpty());
        assertFalse(captor.getValue().getNeedName());
        assertFalse(captor.getValue().getNeedPhoneNumber());
        assertFalse(captor.getValue().getNeedEmail());
        assertFalse(captor.getValue().getNeedShippingAddress());
        assertFalse(captor.getValue().getIsFlexible());
    }

    @Test
    void telegramRequestFailureKeepsStructuredError() throws Exception {
        Payment payment = Payment.builder().payload("opaque-payload").amount(1)
                .currency("XTR").build();
        var response = org.telegram.telegrambots.meta.api.objects.ApiResponse.builder()
                .ok(false).errorCode(400).errorDescription("Bad Request: CURRENCY_TOTAL_AMOUNT_INVALID").build();
        when(client.execute(any(SendInvoice.class))).thenThrow(
                new org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException("request failed", response));

        var failure = assertThrows(ru.murad.yourmarket.exception.TelegramConfirmedFailureException.class,
                () -> gateway.sendInvoice(10L, payment));

        assertEquals(400, failure.getErrorCode());
        assertEquals("Bad Request: CURRENCY_TOTAL_AMOUNT_INVALID", failure.getApiDescription());
        assertTrue(failure.isCurrencyTotalAmountInvalid());
    }

    @Test
    void refundStarsUsesTelegramUserAndChargeId() throws Exception {
        gateway.refundStarPayment(42L, "telegram-charge");

        ArgumentCaptor<RefundStarPayment> captor = ArgumentCaptor.forClass(RefundStarPayment.class);
        verify(client).execute(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals("telegram-charge", captor.getValue().getTelegramPaymentChargeId());
    }

    @Test
    void singlePhotoContainsOnlySellerButtonAndClickableContactInCaption() throws Exception {
        Advertisement advertisement = advertisement("@seller");
        when(photos.findByAdvertisementIdOrderByPosition(advertisement.getId())).thenReturn(List.of());
        Message response = mock(Message.class);
        when(response.getMessageId()).thenReturn(11);
        when(client.execute(any(SendPhoto.class))).thenReturn(response);

        gateway.publishAdvertisementPrimaryMessages(advertisement);

        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(client).execute(captor.capture());
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
        List<String> labels = markup.getKeyboard().stream().flatMap(List::stream).map(button -> button.getText()).toList();
        assertEquals(List.of("✉️ Написать продавцу"), labels);
        assertFalse(labels.contains("➕ Разместить своё объявление"));
        assertTrue(captor.getValue().getCaption().contains("<a href=\"https://t.me/seller\">@seller</a>"));
    }

    @Test
    void singlePhotoWithOtherContactHasTextContactAndNoKeyboard() throws Exception {
        Advertisement advertisement = advertisement("+7 900 000-00-00");
        when(photos.findByAdvertisementIdOrderByPosition(advertisement.getId())).thenReturn(List.of());
        Message response = mock(Message.class);
        when(response.getMessageId()).thenReturn(11);
        when(client.execute(any(SendPhoto.class))).thenReturn(response);

        gateway.publishAdvertisementPrimaryMessages(advertisement);

        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(client).execute(captor.capture());
        assertNull(captor.getValue().getReplyMarkup());
        assertTrue(captor.getValue().getCaption().contains("Продавец: +7 900 000-00-00"));
    }

    @Test
    void mediaGroupDoesNotCreateSeparateActionMessageAndKeepsContactInCaption() throws Exception {
        Advertisement advertisement = advertisement("@seller");
        when(photos.findByAdvertisementIdOrderByPosition(advertisement.getId())).thenReturn(List.of(
                photo(advertisement, 0), photo(advertisement, 1)));
        Message first = mock(Message.class);
        Message second = mock(Message.class);
        when(first.getMessageId()).thenReturn(21);
        when(second.getMessageId()).thenReturn(22);
        when(client.execute(any(SendMediaGroup.class))).thenReturn(List.of(first, second));

        assertEquals(List.of(21, 22), gateway.publishAdvertisementMessages(advertisement));

        ArgumentCaptor<SendMediaGroup> captor = ArgumentCaptor.forClass(SendMediaGroup.class);
        verify(client).execute(captor.capture());
        verify(client, never()).execute(any(SendMessage.class));
        assertFalse(gateway.needsSeparateContactMessage(advertisement));
        assertTrue(captor.getValue().getMedias().getFirst().getCaption()
                .contains("<a href=\"https://t.me/seller\">@seller</a>"));
    }

    private Advertisement advertisement(String contact) {
        return Advertisement.builder().id(UUID.randomUUID()).title("Телефон").description("Хорошее состояние")
                .itemPrice(BigDecimal.TEN).city("Москва").contact(contact).category(AdvertisementCategory.ELECTRONICS)
                .telegramFileId("file").build();
    }

    private AdvertisementPhoto photo(Advertisement advertisement, int position) {
        return AdvertisementPhoto.builder().advertisementId(advertisement.getId())
                .telegramFileId("file-" + position).position(position).build();
    }

    private TelegramProperties properties() {
        return new TelegramProperties(new TelegramProperties.Bot("@your_market_bot", "token"),
                new TelegramProperties.Channel("-1001234567890", "channel", "https://t.me/channel"));
    }
}
