package ru.murad.yourmarket.service;
import ru.murad.yourmarket.model.AdvertisementDraft;
public interface AdvertisementLocationFlowService {
    AdvertisementDraft beginRegionSearch(Long userId); AdvertisementDraft chooseRegion(Long userId, String code);
    AdvertisementDraft beginCitySearch(Long userId); AdvertisementDraft chooseCity(Long userId, String code);
    AdvertisementDraft beginCustomLocality(Long userId); AdvertisementDraft setCustomLocality(Long userId, String locality);
    AdvertisementDraft backToRegions(Long userId);
}
