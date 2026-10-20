package ru.alexey.stayflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtTokenServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final JwtProperties properties =
            new JwtProperties(SECRET, "https://stayflow.example", Duration.ofHours(1), true);
    private final JwtEncoder encoder = JwtEncoderFactory.create(properties);
    private final JwtDecoder decoder = JwtDecoderFactory.create(properties);
    private final JwtTokenService tokenService = new JwtTokenService(encoder, properties);

    @Test
    void issuesTokenWithExpectedClaims() {
        String token = tokenService.issue(42L, "alice", List.of("ROLE_USER"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsString(JwtClaims.USERNAME)).isEqualTo("alice");
        assertThat(((Number) jwt.getClaim(JwtClaims.USER_ID)).longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsStringList(JwtClaims.ROLES)).containsExactly("ROLE_USER");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://stayflow.example");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        String shortSecret = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));
        JwtProperties shortProperties =
                new JwtProperties(shortSecret, "https://stayflow.example", Duration.ofHours(1), false);

        assertThatThrownBy(() -> JwtDecoderFactory.create(shortProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}