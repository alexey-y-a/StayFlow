package ru.alexey.stayflow.common.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record CurrentUser(Long id, String username, Set<String> roles) {

    public CurrentUser {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            return new CurrentUser(
                    extractUserId(jwt),
                    Optional.ofNullable(jwt.getClaimAsString(JwtClaims.USERNAME))
                            .orElse(jwt.getSubject()),
                    new LinkedHashSet<>(Optional.ofNullable(jwt.getClaimAsStringList(JwtClaims.ROLES))
                            .orElse(List.of())));
        }
        throw new IllegalStateException("Current user is not available");
    }

    public boolean isAdmin() {
        return roles.contains("ROLE_ADMIN");
    }

    private static Long extractUserId(Jwt jwt) {
        Object value = jwt.getClaim(JwtClaims.USER_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }
}
