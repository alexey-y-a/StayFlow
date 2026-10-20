package ru.alexey.stayflow.hotel.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelFilter {

    private String city;
    private String name;
    private BigDecimal minRating;
    private BigDecimal maxRating;
}
