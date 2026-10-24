package ru.alexey.stayflow.hotel.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    List<Room> findByHotelIdOrderById(Long hotelId);

    Optional<Room> findByHotelIdAndNumber(Long hotelId, String number);

    long countByHotelId(Long hotelId);

    long countByHotelIdAndAvailableTrue(Long hotelId);

    List<Room> findByAvailableTrueOrderByTimesBookedAscIdAsc();

    List<Room> findByAvailableTrueAndHotelIdOrderByTimesBookedAscIdAsc(Long hotelId);

    List<Room> findByHotelIdOrderByTimesBookedDescIdAsc(Long hotelId, Pageable pageable);

    @Query("""
            select r from Room r
            where r.available = true
              and not exists (
                  select 1 from RoomReservation rr
                  where rr.room = r
                    and rr.status = :status
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate)
            order by r.timesBooked asc, r.id asc
            """)
    List<Room> findRecommended(
            @Param("status") ReservationStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select r from Room r
            where r.available = true
              and r.hotel.id = :hotelId
              and not exists (
                  select 1 from RoomReservation rr
                  where rr.room = r
                    and rr.status = :status
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate)
            order by r.timesBooked asc, r.id asc
            """)
    List<Room> findRecommendedByHotelId(
            @Param("hotelId") Long hotelId,
            @Param("status") ReservationStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("select coalesce(sum(r.timesBooked), 0) from Room r where r.hotel.id = :hotelId")
    long sumTimesBookedByHotelId(@Param("hotelId") Long hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
