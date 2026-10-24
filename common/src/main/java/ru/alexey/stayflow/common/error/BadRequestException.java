package ru.alexey.stayflow.common.error;

import java.util.List;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(ErrorCode.BAD_REQUEST, message, cause);
    }

    public BadRequestException(String message, List<FieldErrorDTO> details) {
        super(ErrorCode.BAD_REQUEST, message, details);
    }
}
