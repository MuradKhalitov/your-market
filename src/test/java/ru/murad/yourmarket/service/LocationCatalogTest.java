package ru.murad.yourmarket.service;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.*;
class LocationCatalogTest {
 private final LocationCatalog catalog=new LocationCatalog(); @BeforeEach void load(){catalog.load();}
 @Test void projectCatalogLoadsWithPriorityRegions(){assertEquals(java.util.List.of("DAGESTAN","CHECHNYA","INGUSHETIA"),catalog.regions().stream().limit(3).map(LocationCatalog.Region::code).toList());}
 @Test void productPriorityOrderMatchesConfiguredFirstPage(){assertEquals(java.util.List.of("DAGESTAN","CHECHNYA","INGUSHETIA","STAVROPOL_KRAI","KRASNODAR_KRAI","ROSTOV_OBLAST","MOSCOW","MOSCOW_OBLAST","SAINT_PETERSBURG","TATARSTAN"),catalog.regions().stream().limit(10).map(LocationCatalog.Region::code).toList());}
 @Test void containsAllRequiredProductRegions(){assertTrue(catalog.regions().size()>=32);assertTrue(catalog.region("KALININGRAD_OBLAST").isPresent());assertTrue(catalog.region("BASHKORTOSTAN").isPresent());}
 @Test void priorityControlsContainRequiredCities(){assertTrue(catalog.cities("DAGESTAN").stream().anyMatch(c->c.code().equals("MAKHACHKALA")));assertTrue(catalog.cities("CHECHNYA").stream().anyMatch(c->c.code().equals("GROZNY")));assertTrue(catalog.cities("INGUSHETIA").stream().anyMatch(c->c.code().equals("NAZRAN")));}
 @Test void searchIsScopedAndCaseInsensitive(){assertTrue(catalog.searchRegions("чеч").stream().anyMatch(r->r.code().equals("CHECHNYA")));assertTrue(catalog.searchCities("DAGESTAN","маха").stream().allMatch(c->c.code().equals("MAKHACHKALA")));assertTrue(catalog.searchCities("CHECHNYA","маха").isEmpty());}
 @Test void codesAreCallbackSafe(){catalog.regions().forEach(r->{assertTrue(r.code().matches("[A-Z0-9_]+"));r.cities().forEach(c->assertTrue(("ad:loc:c:"+c.code()).length()<=64));});}
}
