package ru.alexey.stayflow.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.alexey.stayflow.gateway.web.GatewayErrorWriter;

@Component
@RequiredArgsConstructor
public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
}
