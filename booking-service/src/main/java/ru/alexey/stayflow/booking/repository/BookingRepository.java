package ru.alexey.stayflow.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<Booking> findByRequestId(String requestId);

    @Query("select b from Booking b join fetch b.user where b.id = :id")
    Optional<Booking> findByIdWithUser(@Param("id") UUID id);

    Page<Booking> findByUser_Id(Long userId, Pageable pageable);

    Page<Booking> findByUser_IdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);

    List<Booking> findByCompensationStatus(CompensationStatus status);
}
