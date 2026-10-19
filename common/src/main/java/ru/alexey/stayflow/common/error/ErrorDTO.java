package ru.alexey.stayflow.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDTO(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldErrorDTO> details) {

    public ErrorDTO {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ErrorDTO of(ErrorCode errorCode, String message, String path, String traceId) {
        return of(errorCode, message, path, traceId, List.of());
    }

    public static ErrorDTO of(
            ErrorCode errorCode, String message, String path, String traceId, List<FieldErrorDTO> details) {
        String resolvedMessage = message == null || message.isBlank() ? errorCode.getDefaultMessage() : message;
        return new ErrorDTO(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.name(),
                resolvedMessage,
                path,
                traceId,
                details);
    }
}