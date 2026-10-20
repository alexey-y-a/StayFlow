package ru.alexey.stayflow.hotel.service;

import ru.alexey.stayflow.hotel.dto.OccupancyStatsResponse;

public interface OccupancyService {

    OccupancyStatsResponse getHotelStatistics(Long hotelId);
}
