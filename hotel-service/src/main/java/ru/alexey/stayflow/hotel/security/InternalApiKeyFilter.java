package ru.alexey.stayflow.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.alexey.stayflow.common.error.ErrorCode;
import ru.alexey.stayflow.common.security.SecurityErrorWriter;
import ru.alexey.stayflow.common.web.TraceHeaders;

public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(String expectedKey, ObjectMapper objectMapper) {
        this.expectedKey = expectedKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.matches("/api/rooms/\\d+/confirm-availability")
                || path.matches("/api/rooms/\\d+/release"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(TraceHeaders.INTERNAL_API_KEY);
        boolean valid = providedKey != null
                && MessageDigest.isEqual(
                        expectedKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            SecurityErrorWriter.write(
                    objectMapper, request, response, ErrorCode.UNAUTHORIZED, "Invalid internal API key");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
