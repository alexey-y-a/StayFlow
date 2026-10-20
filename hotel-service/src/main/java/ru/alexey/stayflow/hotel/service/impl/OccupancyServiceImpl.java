package ru.alexey.stayflow.hotel.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.hotel.dto.OccupancyStatsResponse;
import ru.alexey.stayflow.hotel.dto.RoomLoadResponse;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;
import ru.alexey.stayflow.hotel.service.OccupancyService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupancyServiceImpl implements OccupancyService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    @Override
    public OccupancyStatsResponse getHotelStatistics(Long hotelId) {
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new NotFoundException("Hotel not found: " + hotelId));

        LocalDate today = LocalDate.now();
        long totalRooms = roomRepository.countByHotelId(hotelId);
        long availableRooms = roomRepository.countByHotelIdAndAvailableTrue(hotelId);
        long occupiedRooms = reservationRepository.countOccupiedRoomsByHotel(
                hotelId, ReservationStatus.CONFIRMED, today);
        long activeReservations = reservationRepository.countActiveByHotel(
                hotelId, ReservationStatus.CONFIRMED, today);
        long totalTimesBooked = roomRepository.sumTimesBookedByHotelId(hotelId);
        double averageTimesBooked = totalRooms == 0 ? 0.0 : (double) totalTimesBooked / totalRooms;
        double occupancyRate =
                totalRooms == 0 ? 0.0 : round((double) occupiedRooms / totalRooms * 100.0);

        List<RoomLoadResponse> topRooms = roomRepository
                .findByHotelIdOrderByTimesBookedDescIdAsc(hotelId, PageRequest.of(0, 5))
                .stream()
                .map(room -> new RoomLoadResponse(
                        room.getId(),
                        room.getNumber(),
                        room.getTimesBooked(),
                        reservationRepository.countByRoomIdAndStatusAndEndDateGreaterThan(
                                room.getId(), ReservationStatus.CONFIRMED, today)))
                .toList();

        return new OccupancyStatsResponse(
                hotelId,
                hotel.getName(),
                totalRooms,
                availableRooms,
                occupiedRooms,
                activeReservations,
                occupancyRate,
                totalTimesBooked,
                averageTimesBooked,
                topRooms);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
