package ru.alexey.stayflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TraceHeaders.REQUEST_ID));
        String bookingId = request.getHeader(TraceHeaders.BOOKING_ID);

        MDC.put(TraceHeaders.MDC_TRACE_ID, traceId);
        if (StringUtils.hasText(bookingId)) {
            MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId);
        }

        response.setHeader(TraceHeaders.REQUEST_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceHeaders.MDC_TRACE_ID);
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    private String resolveTraceId(String candidate) {
        if (StringUtils.hasText(candidate)
                && candidate.length() <= MAX_TRACE_ID_LENGTH
                && candidate.matches("[A-Za-z0-9._-]+")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}