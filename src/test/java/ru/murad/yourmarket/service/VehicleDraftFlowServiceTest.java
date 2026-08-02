package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.exception.DraftValidationException;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.VehicleDraftDetails;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.repository.VehicleDraftDetailsRepository;
import ru.murad.yourmarket.service.impl.VehicleDraftFlowServiceImpl;

class VehicleDraftFlowServiceTest {
    private final AdvertisementDraftRepository drafts = mock(AdvertisementDraftRepository.class);
    private final VehicleDraftDetailsRepository details = mock(VehicleDraftDetailsRepository.class);
    private final AdvertisementDraft draft = AdvertisementDraft.builder().id(UUID.randomUUID()).telegramUserId(7L)
            .chatId(7L).category(AdvertisementCategory.AUTO).step(AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND).build();
    private VehicleDraftDetails vehicle;
    private VehicleDraftFlowService service;

    @BeforeEach
    void setUp() {
        VehicleCatalog catalog = new VehicleCatalog();
        catalog.load();
        service = new VehicleDraftFlowServiceImpl(drafts, details, catalog,
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));
        when(drafts.findByTelegramUserId(7L)).thenReturn(Optional.of(draft));
        when(drafts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(details.findByAdvertisementDraftId(draft.getId())).thenAnswer(invocation -> Optional.ofNullable(vehicle));
        when(details.save(any())).thenAnswer(invocation -> { vehicle = invocation.getArgument(0); return vehicle; });
    }

    @Test
    void combustionFlowStoresValidatedDetailsAndReachesTitle() {
        service.chooseBrand(7L, "TOYOTA");
        service.chooseModel(7L, "CAMRY");
        service.setYear(7L, "2020");
        service.setTransmission(7L, TransmissionType.AUTOMATIC);
        service.setEngineType(7L, EngineType.PETROL);
        service.setEngineVolume(7L, "2,0");
        service.setMileage(7L, "85 000");
        service.setDriveType(7L, DriveType.AWD);

        assertEquals(AdvertisementCreationStep.WAITING_FOR_TITLE, draft.getStep());
        assertEquals("Toyota", vehicle.getBrandNameSnapshot());
        assertEquals("Camry", vehicle.getModelNameSnapshot());
        assertEquals(85000, vehicle.getMileageKm());
        assertEquals("2.0", vehicle.getEngineVolumeLiters().toPlainString());
    }

    @Test
    void electricFlowSkipsEngineVolume() {
        service.chooseBrand(7L, "TESLA"); service.chooseModel(7L, "MODEL_3"); service.setYear(7L, "2025");
        service.setTransmission(7L, TransmissionType.AUTOMATIC);
        service.setEngineType(7L, EngineType.ELECTRIC);

        assertEquals(AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE, draft.getStep());
        assertNull(vehicle.getEngineVolumeLiters());
    }

    @Test
    void otherBrandAndModelUseSnapshots() {
        service.chooseBrand(7L, "OTHER");
        service.setCustomBrand(7L, "Моя марка");
        service.setCustomModel(7L, "Модель X");

        assertEquals("OTHER", vehicle.getBrandCode());
        assertEquals("Моя марка", vehicle.getBrandNameSnapshot());
        assertEquals("OTHER", vehicle.getModelCode());
        assertEquals("Модель X", vehicle.getModelNameSnapshot());
    }

    @Test
    void returningToBrandsFromModelSearchClearsSelectedModel() {
        service.chooseBrand(7L, "TOYOTA"); service.chooseModel(7L, "CAMRY");
        draft.setStep(AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL_SEARCH);
        service.backToBrands(7L);

        assertEquals(AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND, draft.getStep());
        assertNull(vehicle.getModelCode());
        assertNull(vehicle.getModelNameSnapshot());
    }

    @Test
    void repeatedCallbackDoesNotAdvanceOrDuplicateState() {
        service.chooseBrand(7L, "TOYOTA");
        VehicleDraftDetails saved = vehicle;
        service.chooseBrand(7L, "TOYOTA");

        assertSame(saved, vehicle);
        assertEquals(AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL, draft.getStep());
        verify(details, times(1)).save(any());
    }

    @Test
    void rejectsOutOfRangeYearVolumeAndMileage() {
        assertThrows(DraftValidationException.class, () -> service.setYear(7L, "1899"));
        service.chooseBrand(7L, "TOYOTA"); service.chooseModel(7L, "CAMRY"); service.setYear(7L, "2020");
        service.setTransmission(7L, TransmissionType.MANUAL); service.setEngineType(7L, EngineType.PETROL);
        assertThrows(DraftValidationException.class, () -> service.setEngineVolume(7L, "10.1"));
        service.setEngineVolume(7L, "1.0");
        assertThrows(DraftValidationException.class, () -> service.setMileage(7L, "3000001"));
    }
}
