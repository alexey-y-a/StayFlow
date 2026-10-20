package ru.alexey.stayflow.booking.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.alexey.stayflow.booking.model.BookingEvent;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {

    List<BookingEvent> findByBooking_IdOrderByCreatedAtAsc(UUID bookingId);

    Page<BookingEvent> findByBooking_Id(UUID bookingId, Pageable pageable);
}
