package ru.alexey.stayflow.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.mapper.BookingMapper;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingEvent;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingEventRepository;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.web.TraceHeaders;

/**
 * Локальные транзакции над состоянием бронирования и журналом событий.
 */
@Component
@RequiredArgsConstructor
public class BookingStateService {

    private final BookingRepository bookingRepository;
    private final BookingEventRepository bookingEventRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final TransactionTemplate transactionTemplate;

    public Booking createPending(
            Long userId, Long roomId, Long hotelId, LocalDate startDate, LocalDate endDate, String requestId) {
        return transactionTemplate.execute(status -> {
            User managedUser = userRepository
                    .findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found: " + userId));
            Booking booking = new Booking();
            booking.setId(UUID.randomUUID());
            booking.setUser(managedUser);
            booking.setRoomId(roomId);
            booking.setHotelId(hotelId);
            booking.setStartDate(startDate);
            booking.setEndDate(endDate);
            booking.setStatus(BookingStatus.PENDING);
            booking.setRequestId(requestId);
            booking.setCurrency("RUB");
            bookingRepository.save(booking);
            bookingEventRepository.save(event(booking, null, BookingStatus.PENDING, "Booking created"));
            return booking;
        });
    }

    public void updateRoom(UUID bookingId, Long roomId, Long hotelId) {
        transactionTemplate.executeWithoutResult(status -> {
            Booking booking = bookingRepository
                    .findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
            booking.setRoomId(roomId);
            booking.setHotelId(hotelId);
            bookingRepository.save(booking);
        });
    }

    public BookingResponse markConfirmed(UUID bookingId, Long hotelId, BigDecimal totalPrice) {
        return transactionTemplate.execute(status -> {
            Booking booking = findBooking(bookingId);
            BookingStatus from = booking.getStatus();
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setHotelId(hotelId);
            booking.setTotalPrice(totalPrice);
            booking.setCompensationStatus(CompensationStatus.NONE);
            bookingRepository.save(booking);
            bookingEventRepository.save(event(booking, from, BookingStatus.CONFIRMED, "Availability confirmed"));
            return bookingMapper.toResponse(booking);
        });
    }

    public void markCancelled(UUID bookingId, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            Booking booking = bookingRepository
                    .findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                return;
            }
            BookingStatus from = booking.getStatus();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancellationReason(reason);
            bookingRepository.save(booking);
            bookingEventRepository.save(event(booking, from, BookingStatus.CANCELLED, reason));
        });
    }

    public void markCompensation(UUID bookingId, CompensationStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            Booking booking = bookingRepository
                    .findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
            booking.setCompensationStatus(status);
            bookingRepository.save(booking);
        });
    }

    public Booking findBooking(UUID bookingId) {
        return bookingRepository
                .findByIdWithUser(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
    }

    public BookingResponse getInternal(Booking booking) {
        return transactionTemplate.execute(status -> bookingMapper.toResponse(findBooking(booking.getId())));
    }

    private BookingEvent event(Booking booking, BookingStatus from, BookingStatus to, String reason) {
        BookingEvent event = new BookingEvent();
        event.setBooking(booking);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setReason(reason);
        event.setTraceId(MDC.get(TraceHeaders.MDC_TRACE_ID));
        return event;
    }
}
