package ru.murad.yourmarket.config;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter @Setter @NoArgsConstructor @ConfigurationProperties(prefix="admin") public class AdminProperties {private String apiKey="";public String apiKey(){return apiKey;}}
