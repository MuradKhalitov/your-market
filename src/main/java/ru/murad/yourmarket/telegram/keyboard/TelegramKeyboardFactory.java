package ru.murad.yourmarket.telegram.keyboard;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import ru.murad.yourmarket.config.PublicationProperties;
import ru.murad.yourmarket.config.PaymentsProperties;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import java.util.*;

@Component
public class TelegramKeyboardFactory {
    public static final String CREATE = "➕ Разместить объявление";
    public static final String MY_ADS = "📋 Мои объявления";
    public static final String CHANNEL = "📢 Перейти в канал";
    public static final String RULES = "ℹ️ Правила";
    public static final String CANCEL_CREATION = "❌ Отменить создание";
    public static final String BACK = "⬅️ Назад";
    public static final String PHOTOS_DONE = "✅ Готово";
    public static final String PHOTOS_CLEAR = "🗑 Очистить фотографии";

    private final PublicationProperties publication;
    private final PaymentsProperties payments;

    public TelegramKeyboardFactory(PublicationProperties publication, PaymentsProperties payments) {
        this.publication = publication;
        this.payments = payments;
    }

    public ReplyKeyboardMarkup mainMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(new KeyboardRow(CREATE, MY_ADS), new KeyboardRow(CHANNEL, RULES)))
                .resizeKeyboard(true).isPersistent(true).oneTimeKeyboard(false)
                .inputFieldPlaceholder("Выберите действие").build();
    }

    public ReplyKeyboardMarkup creationNavigation(boolean firstStep) {
        KeyboardRow row = firstStep ? new KeyboardRow(CANCEL_CREATION) : new KeyboardRow(BACK, CANCEL_CREATION);
        return ReplyKeyboardMarkup.builder().keyboard(List.of(row))
                .resizeKeyboard(true).isPersistent(true).oneTimeKeyboard(false).build();
    }

    public ReplyKeyboardMarkup photoNavigation(boolean hasPhotos) {
        List<KeyboardRow> rows = new ArrayList<>();
        if (hasPhotos) { rows.add(new KeyboardRow(PHOTOS_DONE)); rows.add(new KeyboardRow(PHOTOS_CLEAR)); }
        rows.add(new KeyboardRow(BACK, CANCEL_CREATION));
        return ReplyKeyboardMarkup.builder().keyboard(rows).resizeKeyboard(true).isPersistent(true)
                .oneTimeKeyboard(false).build();
    }

    public InlineKeyboardMarkup preview() {
        return inlineRows(callback(payments.isEnabled() ? "Оплатить " + publication.getPriceStars() + " ⭐" : "Отправить на модерацию", "pay"),
                callback("✏️ Изменить", "edit:menu"),
                callback("❌ Отменить", "cancel"));
    }

    public InlineKeyboardMarkup categorySelection() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<AdvertisementCategory> categories = List.of(AdvertisementCategory.values());
        for (int index = 0; index < categories.size(); index += 2) {
            AdvertisementCategory first = categories.get(index);
            if (index + 1 == categories.size()) {
                rows.add(new InlineKeyboardRow(categoryButton(first)));
            } else {
                rows.add(new InlineKeyboardRow(categoryButton(first), categoryButton(categories.get(index + 1))));
            }
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public InlineKeyboardMarkup editMenu() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(callback("Категория", "edit:CATEGORY"), callback("Название", "edit:TITLE")),
                new InlineKeyboardRow(callback("Описание", "edit:DESCRIPTION"), callback("Цена", "edit:PRICE")),
                new InlineKeyboardRow(callback("Фотографии", "edit:PHOTOS"), callback("Город", "edit:CITY")),
                new InlineKeyboardRow(callback("Контакт", "edit:CONTACT")),
                new InlineKeyboardRow(callback("⬅️ К предпросмотру", "edit:preview")))).build();
    }

    public InlineKeyboardMarkup remove(UUID id) {
        return inlineRows(callback("🗑 Снять объявление", "delete:" + id));
    }

    public InlineKeyboardMarkup publishedActions(UUID id, String channelUrl) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (channelUrl != null) rows.add(new InlineKeyboardRow(url("📢 Открыть в канале", channelUrl)));
        rows.add(new InlineKeyboardRow(callback("🗑 Снять объявление", "delete:" + id)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public InlineKeyboardMarkup retryPublication(UUID id) {
        return inlineRows(callback("🔄 Повторить публикацию", "retry:" + id));
    }

    public InlineKeyboardMarkup openChannel(String channelUrl) {
        return channelUrl == null ? null : inlineRows(url("📢 Открыть в канале", channelUrl));
    }

    public InlineKeyboardMarkup moderation(UUID id) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(new InlineKeyboardRow(
                callback("✅ Одобрить", "mod:approve:" + id), callback("❌ Отклонить", "mod:reject:" + id)))).build();
    }

    public InlineKeyboardMarkup seller(String contact) {
        return inlineRows(url("✉️ Написать продавцу", "https://t.me/" + contact.substring(1)));
    }

    private InlineKeyboardMarkup inlineRows(InlineKeyboardButton... buttons) {
        return InlineKeyboardMarkup.builder().keyboard(Arrays.stream(buttons).map(InlineKeyboardRow::new).toList()).build();
    }

    private InlineKeyboardButton callback(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private InlineKeyboardButton categoryButton(AdvertisementCategory category) {
        return callback(category.displayLabel(), "ad:category:" + category.name());
    }

    private InlineKeyboardButton url(String text, String value) {
        return InlineKeyboardButton.builder().text(text).url(value).build();
    }
}
