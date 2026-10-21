package ru.alexey.stayflow.booking.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HotelConfirmResponse(
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
