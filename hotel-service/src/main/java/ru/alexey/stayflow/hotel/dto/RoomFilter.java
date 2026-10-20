package ru.alexey.stayflow.hotel.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import ru.alexey.stayflow.hotel.model.RoomType;

@Getter
@Setter
public class RoomFilter {

    private Long hotelId;
    private RoomType type;
    private Integer minCapacity;
    private BigDecimal maxPrice;
    private Boolean available;
}
