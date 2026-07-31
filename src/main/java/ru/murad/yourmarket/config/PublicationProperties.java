package ru.murad.yourmarket.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "publication")
@Validated
public class PublicationProperties {
    @NotNull @DecimalMin("0.01")
    private BigDecimal price = new BigDecimal("199.00");
    @NotBlank
    private String currency = "RUB";
    private boolean moderationEnabled = false;
    @Positive
    private int lifetimeDays = 30;
    @NotBlank
    private String expirationCron = "0 0 * * * *";
    @Positive
    private int deletionClaimTimeoutSeconds = 180;
    @Positive
    private int expirationClaimTimeoutSeconds = 600;
}
