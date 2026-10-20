package ru.alexey.stayflow.hotel.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(
            """
            select count(rr) from RoomReservation rr
            where rr.room.hotel.id = :hotelId
              and rr.status = :status
              and rr.endDate > :date
            """)
    long countActiveByHotel(
            @Param("hotelId") Long hotelId,
            @Param("status") ReservationStatus status,
            @Param("date") LocalDate date);

    @Query(
            """
            select count(distinct rr.room.id) from RoomReservation rr
            where rr.room.hotel.id = :hotelId
              and rr.status = :status
              and rr.endDate > :date
            """)
    long countOccupiedRoomsByHotel(
            @Param("hotelId") Long hotelId,
            @Param("status") ReservationStatus status,
            @Param("date") LocalDate date);

    default boolean existsOverlapping(Long roomId, LocalDate startDate, LocalDate endDate) {
        return existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                roomId, ReservationStatus.CONFIRMED, endDate, startDate);
    }
}