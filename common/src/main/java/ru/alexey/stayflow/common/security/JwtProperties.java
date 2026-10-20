package ru.alexey.stayflow.common.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stayflow.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        String issuer,
        Duration ttl,
        boolean issue) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "https://stayflow.example";
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
    }
}