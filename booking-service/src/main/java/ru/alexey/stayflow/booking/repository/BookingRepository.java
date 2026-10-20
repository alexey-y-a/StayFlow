package ru.alexey.stayflow.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByRequestId(String requestId);

    Page<Booking> findByUser_Id(Long userId, Pageable pageable);

    Page<Booking> findByUser_IdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);
}
