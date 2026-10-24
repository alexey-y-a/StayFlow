package ru.alexey.stayflow.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.alexey.stayflow.common.web.TraceHeaders;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDTO> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getErrorCode().getStatus().is5xxServerError()) {
            log.error("Request failed with {}", exception.getErrorCode(), exception);
        } else {
            log.debug("Request failed with {}: {}", exception.getErrorCode(), exception.getMessage());
        }
        return response(exception.getErrorCode(), exception.getMessage(), request, exception.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorDTO> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDTO(error.getField(), messageOrDefault(error.getDefaultMessage())))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, "Validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDTO> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorDTO> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDTO(
                        violation.getPropertyPath().toString(), messageOrDefault(violation.getMessage())))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, "Validation failed", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDTO> handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(ErrorCode.BAD_REQUEST, "Malformed JSON request", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String expectedType = exception.getRequiredType() == null
                ? "unknown"
                : exception.getRequiredType().getSimpleName();
        List<FieldErrorDTO> details = List.of(new FieldErrorDTO(exception.getName(), "Expected type: " + expectedType));
        return response(ErrorCode.BAD_REQUEST, "Invalid request parameter", request, details);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorDTO> handleMissingHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        List<FieldErrorDTO> details =
                List.of(new FieldErrorDTO(exception.getHeaderName(), "Required header is missing"));
        return response(ErrorCode.BAD_REQUEST, "Required header is missing", request, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDTO> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        List<FieldErrorDTO> details =
                List.of(new FieldErrorDTO(exception.getParameterName(), "Required parameter is missing"));
        return response(ErrorCode.BAD_REQUEST, "Required parameter is missing", request, details);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDTO> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDTO> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDTO> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(ErrorCode.FORBIDDEN, "Access denied", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDTO> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return response(ErrorCode.UNAUTHORIZED, "Authentication required", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception", exception);
        return response(ErrorCode.INTERNAL_ERROR, "Internal server error", request, List.of());
    }

    private ResponseEntity<ErrorDTO> response(
            ErrorCode errorCode, String message, HttpServletRequest request, List<FieldErrorDTO> details) {
        HttpStatus status = errorCode.getStatus();
        ErrorDTO body = ErrorDTO.of(errorCode, message, request.getRequestURI(), traceId(), details);
        return ResponseEntity.status(status).body(body);
    }

    private String traceId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.MDC_TRACE_ID)).orElse("unknown");
    }

    private String messageOrDefault(String message) {
        return message == null || message.isBlank() ? "Invalid value" : message;
    }
}
