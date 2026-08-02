package ru.murad.yourmarket.telegram;

import org.springframework.stereotype.Component;

@Component
public class TelegramMessageProvider {
    public String terms(int priceStars) {
        return "Условия платной публикации:\n"
                + "• стоимость публикации: " + priceStars + " ⭐;\n"
                + "• публикация выполняется после успешной оплаты;\n"
                + "• объявления проходят правила и, при включённой модерации, модерацию;\n"
                + "• вопросы о возврате Stars рассматривает поддержка бота.";
    }

    public String paymentSupport() {
        return "Поддержка по оплате: обратитесь к администратору бота и укажите дату оплаты и описание проблемы. "
                + "Не отправляйте данные банковской карты. Telegram support не рассматривает споры по покупкам у бота.";
    }

    public String support() {
        return "По общим вопросам обратитесь к администратору YourMarket.";
    }
}
