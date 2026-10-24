package ru.alexey.stayflow.booking.client;

import java.math.BigDecimal;

public record HotelRoomInfo(
        Long id, Long hotelId, String hotelName, String number, BigDecimal pricePerNight, long timesBooked) {}
