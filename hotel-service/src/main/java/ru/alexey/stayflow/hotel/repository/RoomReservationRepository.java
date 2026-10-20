package ru.alexey.stayflow.hotel.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.RoomReservation;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByBookingId(UUID bookingId);

    Optional<RoomReservation> findByRequestId(String requestId);

    boolean existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId, ReservationStatus status, LocalDate endDate, LocalDate startDate);

    boolean existsByRoomId(Long roomId);

    long countByRoomIdAndStatusAndEndDateGreaterThan(
            Long roomId, ReservationStatus status, LocalDate date);

    default boolean existsOverlapping(Long roomId, LocalDate startDate, LocalDate endDate) {
        return existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                roomId, ReservationStatus.CONFIRMED, endDate, startDate);
    }
}