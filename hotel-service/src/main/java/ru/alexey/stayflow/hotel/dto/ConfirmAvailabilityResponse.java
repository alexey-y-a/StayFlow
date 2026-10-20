package ru.alexey.stayflow.hotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConfirmAvailabilityResponse(
        Long roomId,
        UUID bookingId,
        boolean available,
        String roomNumber,
        Long hotelId,
        String hotelName,
        BigDecimal pricePerNight,
        LocalDate startDate,
        LocalDate endDate) {
}
