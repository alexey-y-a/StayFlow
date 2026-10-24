package ru.alexey.stayflow.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ConfirmAvailabilityRequest(
        @NotNull UUID bookingId,
        @NotBlank @Size(max = 100) String requestId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}
