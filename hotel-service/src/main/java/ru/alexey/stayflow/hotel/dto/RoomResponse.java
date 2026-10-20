package ru.alexey.stayflow.hotel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ru.alexey.stayflow.hotel.model.RoomType;

public record RoomResponse(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        RoomType type,
        int capacity,
        BigDecimal pricePerNight,
        int floor,
        boolean available,
        long timesBooked,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
