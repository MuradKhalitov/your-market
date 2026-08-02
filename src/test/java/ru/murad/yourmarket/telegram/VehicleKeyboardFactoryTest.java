package ru.murad.yourmarket.telegram;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.murad.yourmarket.service.VehicleCatalog;
import ru.murad.yourmarket.telegram.keyboard.VehicleKeyboardFactory;

class VehicleKeyboardFactoryTest {
    private VehicleKeyboardFactory keyboards;

    @BeforeEach
    void setUp() {
        VehicleCatalog catalog = new VehicleCatalog();
        catalog.load();
        keyboards = new VehicleKeyboardFactory(catalog);
    }

    @Test
    void brandPaginationContainsPageNavigationSearchAndOther() {
        List<String> callbacks = callbacks(keyboards.brands(1));
        assertTrue(callbacks.contains("ad:auto:bp:0"));
        assertTrue(callbacks.contains("ad:auto:bp:2"));
        assertTrue(callbacks.contains("ad:auto:bs"));
        assertTrue(callbacks.contains("ad:auto:b:OTHER"));
        assertTrue(callbacks.stream().allMatch(value -> value.length() <= 64));
    }

    @Test
    void modelPaginationSupportsBackToBrandsAndOtherModel() {
        List<String> callbacks = callbacks(keyboards.models("TOYOTA", 1));
        assertTrue(callbacks.contains("ad:auto:brands"));
        assertTrue(callbacks.contains("ad:auto:m:OTHER"));
        assertTrue(callbacks.contains("ad:auto:ms"));
    }

    @Test
    void searchesReturnScopedCallbackChoices() {
        assertTrue(callbacks(keyboards.brandSearch("tes")).contains("ad:auto:sb:TESLA"));
        List<String> models = callbacks(keyboards.modelSearch("TESLA", "model"));
        assertTrue(models.stream().anyMatch(value -> value.equals("ad:auto:sm:MODEL_3")));
        assertTrue(models.stream().noneMatch(value -> value.contains("CAMRY")));
    }

    private List<String> callbacks(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream().flatMap(List::stream).map(button -> button.getCallbackData()).toList();
    }
}
