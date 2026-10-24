package ru.alexey.stayflow.common.error;

import java.util.List;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(ErrorCode.CONFLICT, message, cause);
    }

    public ConflictException(String message, List<FieldErrorDTO> details) {
        super(ErrorCode.CONFLICT, message, details);
    }
}
