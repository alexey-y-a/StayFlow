package ru.alexey.stayflow.hotel.service;

import java.time.LocalDate;
import java.util.List;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomResponse;

public interface AvailabilityService {

    ConfirmAvailabilityResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request);

    ReleaseRoomResponse releaseRoom(Long roomId, ReleaseRoomRequest request);

    List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate);
}
