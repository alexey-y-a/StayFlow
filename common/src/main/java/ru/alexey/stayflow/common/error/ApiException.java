package ru.alexey.stayflow.common.error;

import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorDTO> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), cause);
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldErrorDTO> details) {
        this(errorCode, message, details, null);
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldErrorDTO> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldErrorDTO> getDetails() {
        return details;
    }
}