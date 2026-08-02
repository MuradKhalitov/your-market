package ru.murad.yourmarket.telegram.keyboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.murad.yourmarket.model.enums.DriveType;
import ru.murad.yourmarket.model.enums.EngineType;
import ru.murad.yourmarket.model.enums.TransmissionType;
import ru.murad.yourmarket.service.VehicleCatalog;

@Component
public class VehicleKeyboardFactory {
    private static final int PAGE_SIZE = 10;
    private final VehicleCatalog catalog;
    public VehicleKeyboardFactory(VehicleCatalog catalog) { this.catalog = catalog; }
    public InlineKeyboardMarkup brands(int page) { return paged(catalog.brands(), page, "ad:auto:b:", true); }
    public InlineKeyboardMarkup models(String brand, int page) { return paged(catalog.models(brand), page, "ad:auto:m:", false); }
    public InlineKeyboardMarkup brandSearch(String query) { return choices(catalog.searchBrands(query).stream().limit(10).toList(), "ad:auto:sb:", "ad:auto:brands"); }
    public InlineKeyboardMarkup modelSearch(String brand, String query) { return choices(catalog.searchModels(brand, query).stream().limit(10).toList(), "ad:auto:sm:", "ad:auto:models"); }
    public InlineKeyboardMarkup transmission() { return enums(TransmissionType.values(), "ad:auto:t:"); }
    public InlineKeyboardMarkup engine() { return enums(EngineType.values(), "ad:auto:e:"); }
    public InlineKeyboardMarkup drive() { return enums(DriveType.values(), "ad:auto:d:"); }

    private InlineKeyboardMarkup paged(List<?> values, int requestedPage, String prefix, boolean brands) {
        int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = page * PAGE_SIZE, to = Math.min(values.size(), from + PAGE_SIZE);
        List<InlineKeyboardRow> rows = pairRows(values.subList(from, to), prefix);
        rows.add(new InlineKeyboardRow(button("🔎 Найти " + (brands ? "марку" : "модель"),
                brands ? "ad:auto:bs" : "ad:auto:ms")));
        rows.add(new InlineKeyboardRow(button(brands ? "Другая марка" : "Другая модель",
                brands ? "ad:auto:b:OTHER" : "ad:auto:m:OTHER")));
        if (!brands) rows.add(new InlineKeyboardRow(button("⬅️ Назад к маркам", "ad:auto:brands")));
        if (pages > 1) rows.add(new InlineKeyboardRow(
                button("⬅️", "ad:auto:" + (brands ? "bp:" : "mp:") + Math.max(0, page - 1)),
                button((page + 1) + "/" + pages, "ad:auto:page"),
                button("➡️", "ad:auto:" + (brands ? "bp:" : "mp:") + Math.min(pages - 1, page + 1))));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
    private InlineKeyboardMarkup choices(List<?> values, String prefix, String back) {
        List<InlineKeyboardRow> rows = pairRows(values, prefix);
        rows.add(new InlineKeyboardRow(button("⬅️ Назад", back)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
    private List<InlineKeyboardRow> pairRows(List<?> values, String prefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int index = 0; index < values.size(); index += 2) {
            InlineKeyboardButton first = item(values.get(index), prefix);
            if (index + 1 < values.size()) rows.add(new InlineKeyboardRow(first, item(values.get(index + 1), prefix)));
            else rows.add(new InlineKeyboardRow(first));
        }
        return rows;
    }
    private InlineKeyboardMarkup enums(Object[] values, String prefix) { return InlineKeyboardMarkup.builder().keyboard(pairRows(List.of(values), prefix)).build(); }
    private InlineKeyboardButton item(Object value, String prefix) {
        String code; String name;
        if (value instanceof VehicleCatalog.Brand brand) { code = brand.code(); name = brand.name(); }
        else if (value instanceof VehicleCatalog.Model model) { code = model.code(); name = model.name(); }
        else if (value instanceof TransmissionType transmission) { code = transmission.name(); name = transmission.getDisplayName(); }
        else if (value instanceof EngineType engine) { code = engine.name(); name = engine.getDisplayName(); }
        else { DriveType drive = (DriveType) value; code = drive.name(); name = drive.getDisplayName(); }
        return button(name, prefix + code);
    }
    private InlineKeyboardButton button(String text, String data) { return InlineKeyboardButton.builder().text(text).callbackData(data).build(); }
}
