package ru.alexey.stayflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {

    @Test
    void mapsJwtAuthentication() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("bob")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim(JwtClaims.USER_ID, 7L)
                .claim(JwtClaims.USERNAME, "bob")
                .claim(JwtClaims.ROLES, List.of("ROLE_ADMIN"))
                .build();

        CurrentUser user = CurrentUser.from(new JwtAuthenticationToken(jwt));

        assertThat(user.id()).isEqualTo(7L);
        assertThat(user.username()).isEqualTo("bob");
        assertThat(user.roles()).containsExactlyInAnyOrder("ROLE_ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }
}