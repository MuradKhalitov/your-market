package ru.murad.yourmarket.service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Small, safe runtime projection of GAR. The full GAR archive is processed only by the offline generator. */
@Component
public class LocationCatalog {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]+");
    public record City(String code, String name, boolean popular, int sortOrder) {}
    public record Region(String code, String name, boolean popular, int sortOrder, boolean federalCity, List<City> cities) {}
    private List<Region> regions = List.of();

    @PostConstruct public void load() {
        try (InputStream input = getClass().getResourceAsStream("/location-catalog.yaml")) {
            if (input == null) throw new IllegalStateException("location-catalog.yaml is missing");
            LoaderOptions options = new LoaderOptions(); options.setMaxAliasesForCollections(20); options.setCodePointLimit(2_000_000);
            Map<String, Object> root = new Yaml(new SafeConstructor(options)).load(input);
            Object raw = root.get("regions"); if (!(raw instanceof List<?> list)) throw new IllegalStateException("location catalog regions is required");
            List<Region> parsed = new ArrayList<>(); for (Object item : list) parsed.add(region((Map<String, Object>) item)); validate(parsed);
            regions = sortRegions(parsed);
        } catch (RuntimeException ex) { throw ex; } catch (Exception ex) { throw new IllegalStateException("Cannot load location-catalog.yaml", ex); }
    }
    public List<Region> regions() { return regions; }
    public Optional<Region> region(String code) { return regions.stream().filter(r -> r.code().equals(code)).findFirst(); }
    public List<City> cities(String region) { return this.region(region).map(r -> sortCities(r.cities())).orElse(List.of()); }
    public List<Region> searchRegions(String query) { String q=query(query); return regions.stream().filter(r->r.name().toLowerCase(Locale.ROOT).contains(q)).limit(10).toList(); }
    public List<City> searchCities(String region, String query) { String q=query(query); return cities(region).stream().filter(c->c.name().toLowerCase(Locale.ROOT).contains(q)).limit(10).toList(); }
    private Region region(Map<String,Object> raw) { String code=text(raw,"code"), name=text(raw,"name"); Object cities=raw.get("cities"); if(!(cities instanceof List<?> list)) throw new IllegalStateException("region "+code+": cities missing"); List<City> parsed=new ArrayList<>(); for(Object item:list){Map<String,Object> city=(Map<String,Object>)item; parsed.add(new City(text(city,"code"),text(city,"name"),Boolean.TRUE.equals(city.get("popular")),number(city,"sortOrder")));} return new Region(code,name,Boolean.TRUE.equals(raw.get("popular")),number(raw,"sortOrder"),Boolean.TRUE.equals(raw.get("federalCity")),List.copyOf(parsed)); }
    private void validate(List<Region> values) { Set<String> rc=new HashSet<>(); for(Region r:values){ if(!CODE.matcher(r.code()).matches()||"OTHER".equals(r.code())||!rc.add(r.code())||r.name().isBlank()||r.sortOrder()<0||r.cities().isEmpty()) fail(r.code(),null,"invalid region"); Set<String> cc=new HashSet<>(),names=new HashSet<>(); for(City c:r.cities()){if(!CODE.matcher(c.code()).matches()||"OTHER".equals(c.code())||!cc.add(c.code())||c.name().isBlank()||c.sortOrder()<0||!names.add(c.name().toLowerCase(Locale.ROOT))||("ad:loc:c:"+c.code()).length()>64) fail(r.code(),c.code(),"invalid city");}}
        List<String> first=sortRegions(values).stream().limit(3).map(Region::code).toList(); if(!first.equals(List.of("DAGESTAN","CHECHNYA","INGUSHETIA"))) throw new IllegalStateException("location catalog priority regions must be DAGESTAN, CHECHNYA, INGUSHETIA");
        required(values,"DAGESTAN", "МАХАЧКАЛА","ДЕРБЕНТ","ХАСАВЮРТ","КАСПИЙСК","БУЙНАКСК","ИЗБЕРБАШ","КИЗЛЯР","КИЗИЛЮРТ","ДАГЕСТАНСКИЕ ОГНИ","ЮЖНО-СУХОКУМСК"); required(values,"CHECHNYA","ГРОЗНЫЙ","АРГУН","ГУДЕРМЕС","ШАЛИ","УРУС-МАРТАН","КУРЧАЛОЙ"); required(values,"INGUSHETIA","МАГАС","НАЗРАНЬ","МАЛГОБЕК","КАРАБУЛАК","СУНЖА"); }
    private void required(List<Region> values,String region,String... cities){Region r=values.stream().filter(v->v.code().equals(region)).findFirst().orElseThrow(()->new IllegalStateException("missing priority region: "+region)); for(String city:cities)if(r.cities().stream().noneMatch(c->c.name().equalsIgnoreCase(city)))fail(region,null,"required city missing: "+city);}
    private void fail(String r,String c,String issue){throw new IllegalStateException("location catalog "+issue+" regionCode="+r+(c==null?"":" cityCode="+c));}
    private List<Region> sortRegions(List<Region> x){return x.stream().sorted(Comparator.comparing(Region::popular).reversed().thenComparingInt(Region::sortOrder).thenComparing(Region::name,String.CASE_INSENSITIVE_ORDER)).toList();}
    private List<City> sortCities(List<City> x){return x.stream().sorted(Comparator.comparing(City::popular).reversed().thenComparingInt(City::sortOrder).thenComparing(City::name,String.CASE_INSENSITIVE_ORDER)).toList();}
    private String query(String q){String v=q==null?"":q.trim();if(v.length()<2||v.length()>60||v.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Введите от 2 до 60 символов для поиска.");return v.toLowerCase(Locale.ROOT);}
    private String text(Map<String,Object>x,String key){Object v=x.get(key);return v instanceof String s?s.trim():"";} private int number(Map<String,Object>x,String key){Object v=x.get(key);return v instanceof Number n?n.intValue():-1;}
}
