package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VehicleCatalogTest {
    @Test void loadsFullCatalogAndKeepsPopularBrandsFirst() {
        VehicleCatalog catalog = new VehicleCatalog();
        catalog.load();
        assertTrue(catalog.brands().size() >= 35);
        assertEquals("LADA", catalog.brands().getFirst().code());
        assertTrue(catalog.models("TOYOTA").stream().anyMatch(model -> model.code().equals("CAMRY")));
    }
    @Test void searchIsCaseInsensitiveAndScopedToBrand() {
        VehicleCatalog catalog = new VehicleCatalog(); catalog.load();
        assertTrue(catalog.searchBrands("toy").stream().anyMatch(value -> value.code().equals("TOYOTA")));
        assertTrue(catalog.searchModels("TOYOTA", "cam").stream().allMatch(value -> value.code().equals("CAMRY")));
        assertTrue(catalog.searchBrands("zzzz").isEmpty());
    }
    @Test void catalogCodesAndCallbacksAreSafe() {
        VehicleCatalog catalog = new VehicleCatalog(); catalog.load();
        catalog.brands().forEach(brand -> {
            assertTrue(brand.code().matches("[A-Z0-9_]+"));
            assertFalse("OTHER".equals(brand.code()));
            assertFalse(brand.models().isEmpty());
            brand.models().forEach(model -> { assertTrue(model.code().matches("[A-Z0-9_]+")); assertFalse("OTHER".equals(model.code())); assertTrue(("ad:auto:m:" + model.code()).length() <= 64); });
        });
    }
}
