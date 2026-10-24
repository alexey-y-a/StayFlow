package ru.alexey.stayflow.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import ru.alexey.stayflow.common.security.ApiAccessDeniedHandler;
import ru.alexey.stayflow.common.security.ApiAuthenticationEntryPoint;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!test")
public class SecurityConfig {

    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter(
            @Value("${stayflow.security.internal-api-key}") String internalApiKey, ObjectMapper objectMapper) {
        return new InternalApiKeyFilter(internalApiKey, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, InternalApiKeyFilter internalApiKeyFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalApiKeyFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers(
                                "/api/rooms/*/confirm-availability",
                                "/api/rooms/*/release",
                                "/api/rooms/internal/recommend")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/h2-console/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                        .hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.POST, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
