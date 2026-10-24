package ru.alexey.stayflow.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateBookingRequest(
        Long roomId,
        Long hotelId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean autoSelect) {}
