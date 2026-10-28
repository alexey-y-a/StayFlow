package ru.alexey.stayflow.booking.service;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.common.error.BadRequestException;
import ru.alexey.stayflow.common.error.IdempotencyConflictException;
import ru.alexey.stayflow.common.validation.DateRanges;

@Component
public class BookingRequestValidator {

    public void validateRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        if (requestId.length() > 80) {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters");
        }
    }

    public void validateDates(LocalDate startDate, LocalDate endDate) {
        DateRanges.requireFutureRange(startDate, endDate);
    }

    public void verifyIdempotentPayload(Booking existing, CreateBookingRequest request) {
        boolean sameDates = Objects.equals(existing.getStartDate(), request.startDate())
                && Objects.equals(existing.getEndDate(), request.endDate());
        boolean sameHotel = request.hotelId() == null
                || existing.getHotelId() == null
                || Objects.equals(existing.getHotelId(), request.hotelId());
        boolean sameRoom = request.autoSelect() || Objects.equals(existing.getRoomId(), request.roomId());
        if (!sameDates || !sameHotel || !sameRoom) {
            throw new IdempotencyConflictException("Idempotency-Key has already been used with a different payload");
        }
    }
}
