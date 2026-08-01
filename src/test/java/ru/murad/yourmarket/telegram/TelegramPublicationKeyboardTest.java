package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementPhoto;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.service.impl.TelegramBotLinkServiceImpl;
import ru.murad.yourmarket.telegram.keyboard.TelegramKeyboardFactory;

class TelegramPublicationKeyboardTest {
    private final TelegramClient client = mock(TelegramClient.class);
    private final AdvertisementPhotoRepository photos = mock(AdvertisementPhotoRepository.class);
    private final TelegramProperties telegram = properties();
    private final TelegramGatewayImpl gateway = new TelegramGatewayImpl(client, telegram, new PublicationProperties(),
            new TelegramKeyboardFactory(new PublicationProperties()), photos, new TelegramBotLinkServiceImpl(telegram));

    @Test
    void singlePhotoContainsPublishButtonAndSellerButton() throws Exception {
        Advertisement advertisement = advertisement("@seller");
        when(photos.findByAdvertisementIdOrderByPosition(advertisement.getId())).thenReturn(List.of());
        Message response = mock(Message.class);
        when(response.getMessageId()).thenReturn(11);
        when(client.execute(any(SendPhoto.class))).thenReturn(response);

        gateway.publishAdvertisementPrimaryMessages(advertisement);

        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        org.mockito.Mockito.verify(client).execute(captor.capture());
        assertButtons((InlineKeyboardMarkup) captor.getValue().getReplyMarkup(), true);
    }

    @Test
    void mediaGroupActionContainsPublishButtonWithoutLosingSellerButton() throws Exception {
        Advertisement advertisement = advertisement("@seller");
        when(photos.findByAdvertisementIdOrderByPosition(advertisement.getId())).thenReturn(List.of(
                photo(advertisement, 0), photo(advertisement, 1)));
        Message response = mock(Message.class);
        when(response.getMessageId()).thenReturn(12);
        when(client.execute(any(SendMessage.class))).thenReturn(response);

        assertTrue(gateway.needsSeparateContactMessage(advertisement));
        gateway.publishAdvertisementContactMessage(advertisement);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        org.mockito.Mockito.verify(client).execute(captor.capture());
        assertButtons((InlineKeyboardMarkup) captor.getValue().getReplyMarkup(), true);
    }

    private void assertButtons(InlineKeyboardMarkup markup, boolean sellerExpected) {
        List<String> labels = markup.getKeyboard().stream().flatMap(List::stream).map(button -> button.getText()).toList();
        assertTrue(labels.contains("➕ Разместить своё объявление"));
        assertEquals(sellerExpected, labels.contains("✉️ Написать продавцу"));
    }

    private Advertisement advertisement(String contact) {
        return Advertisement.builder().id(UUID.randomUUID()).title("Телефон").description("Хорошее состояние")
                .itemPrice(BigDecimal.TEN).city("Москва").contact(contact).category(AdvertisementCategory.ELECTRONICS)
                .telegramFileId("file").build();
    }

    private AdvertisementPhoto photo(Advertisement advertisement, int position) {
        return AdvertisementPhoto.builder().advertisementId(advertisement.getId()).telegramFileId("file-" + position).position(position).build();
    }

    private TelegramProperties properties() {
        return new TelegramProperties(new TelegramProperties.Bot("@your_market_bot", "token"),
                new TelegramProperties.Channel("-1001234567890", "channel", "https://t.me/channel"),
                new TelegramProperties.Payment("provider"));
    }
}
