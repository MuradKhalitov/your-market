package ru.murad.yourmarket.config;

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
    private boolean moderationEnabled = false;
    @Positive
    private int lifetimeDays = 30;
    @NotBlank
    private String expirationCron = "0 0 * * * *";
    @Positive
    private int deletionClaimTimeoutSeconds = 180;
    @Positive
    private int expirationClaimTimeoutSeconds = 600;
    @Positive
    private int refundClaimTimeoutSeconds = 600;
}
