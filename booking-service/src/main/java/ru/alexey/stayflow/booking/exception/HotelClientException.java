package ru.alexey.stayflow.booking.exception;

import ru.alexey.stayflow.common.error.ApiException;
import ru.alexey.stayflow.common.error.ErrorCode;

public class HotelClientException extends ApiException {

    public HotelClientException(String message, Throwable cause) {
        super(ErrorCode.REMOTE_SERVICE_ERROR, message, cause);
    }
}
