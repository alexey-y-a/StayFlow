package ru.alexey.stayflow.hotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import ru.alexey.stayflow.hotel.model.RoomType;

public record RoomCreateRequest(
        @NotNull Long hotelId,
        @NotBlank @Size(max = 20) String number,
        @NotNull RoomType type,
        @NotNull @Min(1) @Max(10) Integer capacity,
        @NotNull @DecimalMin("0.01") BigDecimal pricePerNight,
        @NotNull @Min(0) Integer floor,
        Boolean available,
        @Size(max = 500) String description) {
}
