package ru.alexey.stayflow.hotel.service;

import java.time.LocalDate;
import java.util.List;
import ru.alexey.stayflow.hotel.dto.RoomResponse;

public interface RoomRecommendationService {

    List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate);
}
