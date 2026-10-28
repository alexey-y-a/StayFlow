package ru.alexey.stayflow.hotel.service.impl;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.validation.DateRanges;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.mapper.RoomMapper;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.service.RoomRecommendationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRecommendationServiceImpl implements RoomRecommendationService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        DateRanges.requireOptionalFutureRange(startDate, endDate);
        if (hotelId != null && !hotelRepository.existsById(hotelId)) {
            throw new NotFoundException("Hotel not found: " + hotelId);
        }

        List<Room> rooms;
        if (startDate == null) {
            rooms = hotelId == null
                    ? roomRepository.findByAvailableTrueOrderByTimesBookedAscIdAsc()
                    : roomRepository.findByAvailableTrueAndHotelIdOrderByTimesBookedAscIdAsc(hotelId);
        } else {
            rooms = hotelId == null
                    ? roomRepository.findRecommended(ReservationStatus.CONFIRMED, startDate, endDate)
                    : roomRepository.findRecommendedByHotelId(hotelId, ReservationStatus.CONFIRMED, startDate, endDate);
        }
        log.debug("Recommended {} rooms for hotel {} and dates {} - {}", rooms.size(), hotelId, startDate, endDate);
        return rooms.stream().map(roomMapper::toResponse).toList();
    }
}
