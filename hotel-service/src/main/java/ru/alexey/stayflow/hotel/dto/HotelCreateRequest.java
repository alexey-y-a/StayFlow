package ru.alexey.stayflow.hotel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record HotelCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 1000) String description,
        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating) {
}
