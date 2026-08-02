package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.exception.InvalidAdvertisementStateException;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.*;
import ru.murad.yourmarket.service.impl.VehicleDetailsServiceImpl;

class VehicleDetailsServiceTest {
    private final VehicleDraftDetailsRepository drafts = mock(VehicleDraftDetailsRepository.class);
    private final VehicleDetailsRepository details = mock(VehicleDetailsRepository.class);
    private final VehicleDetailsService service = new VehicleDetailsServiceImpl(drafts, details, new VehicleDetailsFormatter());

    @Test
    void copiesImmutableSnapshotsExactlyOnce() {
        AdvertisementDraft draft = AdvertisementDraft.builder().id(UUID.randomUUID()).category(AdvertisementCategory.AUTO).build();
        Advertisement advertisement = Advertisement.builder().id(UUID.randomUUID()).category(AdvertisementCategory.AUTO).build();
        VehicleDraftDetails source = completeDraft();
        when(drafts.findByAdvertisementDraftId(draft.getId())).thenReturn(Optional.of(source));
        when(details.findByAdvertisementId(advertisement.getId())).thenReturn(Optional.empty(), Optional.of(VehicleDetails.builder().build()));

        service.copyToAdvertisement(draft, advertisement);
        service.copyToAdvertisement(draft, advertisement);

        verify(details, times(2)).findByAdvertisementId(advertisement.getId());
        verify(details).save(argThat(value -> value.getAdvertisementId().equals(advertisement.getId())
                && value.getBrandNameSnapshot().equals("Toyota") && value.getModelNameSnapshot().equals("Camry")));
    }

    @Test
    void rejectsIncompleteAutoDraftBeforePayment() {
        AdvertisementDraft draft = AdvertisementDraft.builder().id(UUID.randomUUID()).category(AdvertisementCategory.AUTO).build();
        Advertisement advertisement = Advertisement.builder().id(UUID.randomUUID()).category(AdvertisementCategory.AUTO).build();
        when(details.findByAdvertisementId(advertisement.getId())).thenReturn(Optional.empty());
        when(drafts.findByAdvertisementDraftId(draft.getId())).thenReturn(Optional.of(VehicleDraftDetails.builder().build()));

        assertThrows(InvalidAdvertisementStateException.class, () -> service.copyToAdvertisement(draft, advertisement));
        verify(details, never()).save(any());
    }

    @Test
    void nonAutoAdvertisementDoesNotCreateVehicleRecord() {
        service.copyToAdvertisement(AdvertisementDraft.builder().category(AdvertisementCategory.OTHER).build(),
                Advertisement.builder().id(UUID.randomUUID()).category(AdvertisementCategory.OTHER).build());
        verifyNoInteractions(drafts, details);
    }

    private VehicleDraftDetails completeDraft() {
        return VehicleDraftDetails.builder().brandCode("TOYOTA").brandNameSnapshot("Toyota")
                .modelCode("CAMRY").modelNameSnapshot("Camry").productionYear(2020)
                .transmission(TransmissionType.AUTOMATIC).engineType(EngineType.PETROL)
                .engineVolumeLiters(new BigDecimal("2.0")).mileageKm(85000).driveType(DriveType.AWD).build();
    }
}
