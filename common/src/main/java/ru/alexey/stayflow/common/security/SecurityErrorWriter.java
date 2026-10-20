package ru.alexey.stayflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import ru.alexey.stayflow.common.error.ErrorCode;
import ru.alexey.stayflow.common.error.ErrorDTO;
import ru.alexey.stayflow.common.web.TraceHeaders;

final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String message)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorDTO body = ErrorDTO.of(errorCode, message, request.getRequestURI(), traceId(), List.of());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static String traceId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.MDC_TRACE_ID)).orElse("unknown");
    }
}
