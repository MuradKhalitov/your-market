package ru.murad.yourmarket.service;

import org.springframework.stereotype.Component;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementDraft;

@Component public class LocationFormatter {
    public String format(Advertisement value) { return format(value.getRegionNameSnapshot(), value.getCityNameSnapshot(), value.getCustomLocality(), value.getCity()); }
    public String format(AdvertisementDraft value) { return format(value.getRegionNameSnapshot(), value.getCityNameSnapshot(), value.getCustomLocality(), value.getCity()); }
    public String format(String region,String city,String custom,String legacy) { String locality=city!=null?city:custom; if(locality==null||locality.isBlank())return legacy==null?"":legacy; if(region==null||region.isBlank()||region.equalsIgnoreCase(locality))return locality; return region+", "+locality; }
}
