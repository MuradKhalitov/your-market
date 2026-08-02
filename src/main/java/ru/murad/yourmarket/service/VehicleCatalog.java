package ru.murad.yourmarket.service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class VehicleCatalog {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]+");
    private static final int CALLBACK_LIMIT = 64;
    private List<Brand> brands = List.of();
    public record Model(String code, String name, boolean popular, int sortOrder) {}
    public record Brand(String code, String name, boolean popular, int sortOrder, List<Model> models) {}

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        LoaderOptions options = new LoaderOptions(); options.setMaxAliasesForCollections(20); options.setCodePointLimit(1_000_000);
        try (InputStream input = getClass().getResourceAsStream("/vehicle-catalog.yaml")) {
            if (input == null) throw new IllegalStateException("vehicle-catalog.yaml is missing");
            Map<String,Object> root = new Yaml(new SafeConstructor(options)).load(input);
            Object rawBrands = root == null ? null : root.get("brands");
            if (!(rawBrands instanceof List<?> list)) throw new IllegalStateException("vehicle-catalog.yaml: brands is required");
            List<Brand> parsed = new ArrayList<>();
            for (Object raw : list) parsed.add(parseBrand((Map<String,Object>) raw));
            validate(parsed);
            brands = sortedBrands(parsed);
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Cannot load vehicle-catalog.yaml", exception); }
    }
    public List<Brand> brands(){return brands;} public Optional<Brand> brand(String code){return brands.stream().filter(b->b.code().equals(code)).findFirst();}
    public List<Model> models(String brandCode){return brand(brandCode).map(b->sortedModels(b.models())).orElse(List.of());}
    public boolean isBrand(String value){return brand(value).isPresent();}
    public boolean isModel(String brand,String value){return models(brand).stream().anyMatch(v->v.code().equals(value));}
    public List<Brand> searchBrands(String query){String q=normalizedQuery(query);return brands.stream().filter(b->b.name().toLowerCase(Locale.ROOT).contains(q)).limit(10).toList();}
    public List<Model> searchModels(String brand,String query){String q=normalizedQuery(query);return models(brand).stream().filter(m->m.name().toLowerCase(Locale.ROOT).contains(q)).limit(10).toList();}
    private Brand parseBrand(Map<String,Object> raw){String code=string(raw,"code"),name=string(raw,"name");boolean popular=Boolean.TRUE.equals(raw.get("popular"));int order=number(raw,"sortOrder");Object rawModels=raw.get("models");if(!(rawModels instanceof List<?> list))throw new IllegalStateException("vehicle catalog brand "+code+" has no models");List<Model> models=new ArrayList<>();for(Object model:list){Map<String,Object> m=(Map<String,Object>)model;models.add(new Model(string(m,"code"),string(m,"name"),Boolean.TRUE.equals(m.get("popular")),number(m,"sortOrder")));}return new Brand(code,name,popular,order,List.copyOf(models));}
    private String string(Map<String,Object> map,String key){Object value=map.get(key);return value instanceof String s?s.trim():"";}
    private int number(Map<String,Object> map,String key){
        Object value=map.get(key);
        if(value instanceof Number n)return n.intValue();
        // Keep catalog validation deterministic even when a human omitted a space after ':' in flow YAML.
        return map.keySet().stream().filter(candidate->candidate.startsWith(key+":")).findFirst().map(candidate->{try{return Integer.parseInt(candidate.substring(key.length()+1));}catch(NumberFormatException ignored){return -1;}}).orElse(-1);
    }
    private void validate(List<Brand> values){Set<String> codes=new HashSet<>();for(Brand b:values){if(!CODE.matcher(b.code()).matches()||"OTHER".equals(b.code())||!codes.add(b.code()))throw new IllegalStateException("Invalid or duplicate vehicle brand code: "+b.code());if(b.name().isBlank()||b.sortOrder()<0||b.models().isEmpty())throw new IllegalStateException("Invalid vehicle brand: "+b.code());Set<String> modelCodes=new HashSet<>(),names=new HashSet<>();for(Model m:b.models()){if(!CODE.matcher(m.code()).matches()||"OTHER".equals(m.code())||!modelCodes.add(m.code()))throw new IllegalStateException("Invalid or duplicate model code for "+b.code()+": "+m.code());if(m.name().isBlank()||m.sortOrder()<0||!names.add(m.name().toLowerCase(Locale.ROOT)))throw new IllegalStateException("Invalid model for "+b.code()+": "+m.code());if(("ad:auto:b:"+b.code()).length()>CALLBACK_LIMIT||("ad:auto:m:"+m.code()).length()>CALLBACK_LIMIT)throw new IllegalStateException("Callback data is too long for "+b.code()+"/"+m.code());}}}
    private List<Brand> sortedBrands(List<Brand> values){return values.stream().sorted(Comparator.comparing(Brand::popular).reversed().thenComparingInt(Brand::sortOrder).thenComparing(Brand::name,String.CASE_INSENSITIVE_ORDER)).toList();}
    private List<Model> sortedModels(List<Model> values){return values.stream().sorted(Comparator.comparing(Model::popular).reversed().thenComparingInt(Model::sortOrder).thenComparing(Model::name,String.CASE_INSENSITIVE_ORDER)).toList();}
    private String normalizedQuery(String query){String v=query==null?"":query.trim();if(v.length()<2||v.length()>40)throw new IllegalArgumentException("Введите от 2 до 40 символов.");return v.toLowerCase(Locale.ROOT);}
}
