package ru.alexey.stayflow.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ru.alexey.stayflow.booking.model.BookingStatus;

public record BookingResponse(
        UUID id,
        Long userId,
        String username,
        Long roomId,
        Long hotelId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        BigDecimal totalPrice,
        String currency,
        String requestId,
        Instant createdAt,
        Instant updatedAt) {
}
