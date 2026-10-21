package ru.alexey.stayflow.booking.client;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HotelGateway {

    List<HotelRoomInfo> recommend(Long hotelId, LocalDate startDate, LocalDate endDate);

    HotelConfirmResponse confirmAvailability(
            Long roomId, UUID bookingId, String requestId, LocalDate startDate, LocalDate endDate);

    void release(Long roomId, UUID bookingId, String requestId);
}
