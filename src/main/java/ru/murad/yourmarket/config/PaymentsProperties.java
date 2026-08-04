package ru.murad.yourmarket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "yourmarket.payments")
public class PaymentsProperties { private boolean enabled = false; }
