package ru.alexey.stayflow.hotel.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record HotelResponse(
        Long id,
        String name,
        String city,
        String address,
        String description,
        BigDecimal rating,
        Instant createdAt,
        Instant updatedAt) {
}
