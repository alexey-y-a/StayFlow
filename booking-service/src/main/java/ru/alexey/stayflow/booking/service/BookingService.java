package ru.alexey.stayflow.booking.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.common.web.PageResponse;

public interface BookingService {

    BookingResponse create(String username, String requestId, CreateBookingRequest request);

    PageResponse<BookingResponse> history(String username, BookingStatus status, Pageable pageable);

    BookingResponse get(String username, UUID bookingId, boolean admin);

    void cancel(String username, UUID bookingId, boolean admin);
}
