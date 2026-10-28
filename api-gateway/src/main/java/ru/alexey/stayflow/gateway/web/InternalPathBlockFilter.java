package ru.alexey.stayflow.gateway.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalPathBlockFilter implements WebFilter {

    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean internal = path.startsWith("/api/rooms/internal/")
                || path.endsWith("/confirm-availability")
                || path.endsWith("/release");
        if (internal) {
            log.warn("Blocked internal path {}", path);
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        }
        return chain.filter(exchange);
    }
}
