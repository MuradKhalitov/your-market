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
    @NotNull @Min(1)
    private Integer priceStars = 1;
    /** Historical RUB settings are retained only for backward-compatible configuration binding. */
    @Deprecated private BigDecimal price = new BigDecimal("199.00");
    @Deprecated private String currency = "RUB";
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
