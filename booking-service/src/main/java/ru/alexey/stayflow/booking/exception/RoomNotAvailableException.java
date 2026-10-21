package ru.alexey.stayflow.booking.exception;

import ru.alexey.stayflow.common.error.ConflictException;

public class RoomNotAvailableException extends ConflictException {

    public RoomNotAvailableException(String message) {
        super(message);
    }
}
