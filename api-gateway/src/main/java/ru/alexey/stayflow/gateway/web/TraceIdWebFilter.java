package ru.alexey.stayflow.gateway.web;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    private static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID));
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set(GatewayHeaders.REQUEST_ID, traceId);
                    headers.remove(GatewayHeaders.INTERNAL_API_KEY);
                    headers.remove(GatewayHeaders.BOOKING_ID);
                })
                .build();

        exchange.getAttributes().put(GatewayHeaders.TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, traceId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null
                && !candidate.isBlank()
                && candidate.length() <= MAX_TRACE_ID_LENGTH
                && candidate.matches("[A-Za-z0-9._-]+")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
