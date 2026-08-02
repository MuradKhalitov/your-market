package ru.murad.yourmarket.telegram;

import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.Payment;
import java.util.List;

public interface TelegramGateway {
    Integer publishAdvertisement(Advertisement advertisement);
    default List<Integer> publishAdvertisementMessages(Advertisement advertisement) {
        return List.of(publishAdvertisement(advertisement));
    }
    default List<Integer> publishAdvertisementPrimaryMessages(Advertisement advertisement) {
        return publishAdvertisementMessages(advertisement);
    }
    default boolean needsSeparateContactMessage(Advertisement advertisement) { return false; }
    default Integer publishAdvertisementContactMessage(Advertisement advertisement) { throw new UnsupportedOperationException(); }
    void deleteChannelMessage(Integer messageId);
    void sendInvoice(Long chatId, Payment payment);
    void refundStarPayment(Long userId, String telegramPaymentChargeId);
    void sendText(Long chatId, String text);
    Integer sendModerationRequest(Advertisement advertisement);
    default List<Integer> sendModerationMedia(Advertisement advertisement){return List.of(sendModerationRequest(advertisement));}
    default Integer sendModerationAction(Advertisement advertisement){throw new UnsupportedOperationException();}
    void deleteMessage(String chatId, Integer messageId);
}
