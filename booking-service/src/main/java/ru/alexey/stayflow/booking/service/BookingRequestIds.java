package ru.alexey.stayflow.booking.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Детерминированные идентификаторы внутренних вызовов Hotel, чтобы повторная доставка была идемпотентной.
 */
public final class BookingRequestIds {

    private BookingRequestIds() {}

    public static String confirm(String requestId, Long roomId) {
        return UUID.nameUUIDFromBytes((requestId + ":confirm:" + roomId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    public static String release(String requestId) {
        return UUID.nameUUIDFromBytes((requestId + ":release").getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
