package ru.murad.yourmarket.service;
import java.util.*; import ru.murad.yourmarket.model.*; import ru.murad.yourmarket.model.enums.*;
public interface VehicleDraftFlowService {
 AdvertisementDraft chooseBrand(Long userId,String code); AdvertisementDraft setCustomBrand(Long userId,String value);
 AdvertisementDraft chooseModel(Long userId,String code); AdvertisementDraft setCustomModel(Long userId,String value);
 AdvertisementDraft setYear(Long userId,String value); AdvertisementDraft setTransmission(Long userId,TransmissionType value);
 AdvertisementDraft setEngineType(Long userId,EngineType value); AdvertisementDraft setEngineVolume(Long userId,String value);
 AdvertisementDraft setMileage(Long userId,String value); AdvertisementDraft setDriveType(Long userId,DriveType value);
 AdvertisementDraft beginBrandSearch(Long userId); AdvertisementDraft beginModelSearch(Long userId);
 AdvertisementDraft backToBrands(Long userId); AdvertisementDraft setBrandSearchResult(Long userId, String code);
 Optional<VehicleDraftDetails> find(Long userId); void deleteForDraft(UUID draftId);
}
