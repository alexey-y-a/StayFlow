package ru.alexey.stayflow.booking.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.service.BookingPriceCalculator;
import ru.alexey.stayflow.booking.service.BookingRecoveryService;
import ru.alexey.stayflow.booking.service.BookingRequestIds;
import ru.alexey.stayflow.booking.service.BookingStateService;
import ru.alexey.stayflow.common.error.RemoteServiceException;
import ru.alexey.stayflow.common.web.TraceHeaders;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRecoveryServiceImpl implements BookingRecoveryService {

    private final BookingRepository bookingRepository;
    private final HotelGateway hotelGateway;
    private final BookingStateService bookingStateService;
    private final BookingPriceCalculator bookingPriceCalculator;

    @Value("${stayflow.booking.recovery.pending-timeout:PT2M}")
    private Duration pendingTimeout;

    @Override
    public void recover() {
        Instant cutoff = Instant.now().minus(pendingTimeout);
        for (Booking booking : bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)) {
            recoverPending(booking);
        }
        for (Booking booking : bookingRepository.findByCompensationStatus(CompensationStatus.PENDING)) {
            retryCompensation(booking);
        }
    }

    private void recoverPending(Booking booking) {
        UUID bookingId = booking.getId();
        MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId.toString());
        try {
            HotelConfirmResponse confirmation = hotelGateway.confirmAvailability(
                    booking.getRoomId(),
                    bookingId,
                    BookingRequestIds.confirm(booking.getRequestId(), booking.getRoomId()),
                    booking.getStartDate(),
                    booking.getEndDate());
            BigDecimal totalPrice = bookingPriceCalculator.totalPrice(
                    confirmation.pricePerNight(), booking.getStartDate(), booking.getEndDate());
            bookingStateService.markConfirmed(bookingId, confirmation.hotelId(), totalPrice);
            log.info("Recovery confirmed booking {}", bookingId);
        } catch (RoomNotAvailableException exception) {
            bookingStateService.markCancelled(bookingId, "Room is not available");
            boolean released = releaseQuietly(booking.getRoomId(), bookingId, booking.getRequestId());
            bookingStateService.markCompensation(
                    bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
            log.info("Recovery cancelled booking {}, compensation {}", bookingId, released ? "DONE" : "PENDING");
        } catch (RemoteServiceException exception) {
            log.warn("Recovery postponed for booking {}: {}", bookingId, exception.getMessage());
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    private void retryCompensation(Booking booking) {
        MDC.put(TraceHeaders.MDC_BOOKING_ID, booking.getId().toString());
        try {
            boolean released = releaseQuietly(booking.getRoomId(), booking.getId(), booking.getRequestId());
            if (released) {
                bookingStateService.markCompensation(booking.getId(), CompensationStatus.DONE);
                log.info("Compensation completed for booking {}", booking.getId());
            } else {
                log.warn("Compensation still pending for booking {}", booking.getId());
            }
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    private boolean releaseQuietly(Long roomId, UUID bookingId, String requestId) {
        try {
            hotelGateway.release(roomId, bookingId, BookingRequestIds.release(requestId));
            return true;
        } catch (RemoteServiceException exception) {
            log.error("Compensation release failed for booking {} and room {}", bookingId, roomId, exception);
            return false;
        }
    }
}
