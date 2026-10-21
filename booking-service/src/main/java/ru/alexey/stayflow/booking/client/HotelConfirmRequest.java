package ru.alexey.stayflow.booking.client;

import java.time.LocalDate;
import java.util.UUID;

public record HotelConfirmRequest(
        UUID bookingId,
        String requestId,
        LocalDate startDate,
        LocalDate endDate) {
}
