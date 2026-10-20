package ru.alexey.stayflow.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReleaseRoomRequest(
        @NotNull UUID bookingId,
        @NotBlank @Size(max = 100) String requestId) {
}
