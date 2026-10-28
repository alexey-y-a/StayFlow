package ru.alexey.stayflow.hotel.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomResponse;
import ru.alexey.stayflow.hotel.model.ProcessedRequest;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomReservation;
import ru.alexey.stayflow.hotel.repository.ProcessedRequestRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;

/**
 * Повторная доставка внутренних запросов и сборка ответов по сохранённым броням слотов.
 */
@Component
@RequiredArgsConstructor
public class ReservationReplayService {

    static final String CONFIRM_OPERATION = "CONFIRM_AVAILABILITY";
    static final String RELEASE_OPERATION = "RELEASE";
    static final String CONFIRMED_RESULT = "CONFIRMED";
    static final String RELEASED_RESULT = "RELEASED";

    private final ProcessedRequestRepository processedRequestRepository;
    private final RoomReservationRepository reservationRepository;

    public ConfirmAvailabilityResponse replayConfirm(
            ProcessedRequest processed, Long roomId, ConfirmAvailabilityRequest request) {
        boolean samePayload = CONFIRM_OPERATION.equals(processed.getOperation())
                && processed.getRoomId().equals(roomId)
                && processed.getBookingId().equals(request.bookingId())
                && Objects.equals(processed.getStartDate(), request.startDate())
                && Objects.equals(processed.getEndDate(), request.endDate());
        if (!samePayload) {
            throw new ConflictException("requestId already used with a different payload");
        }
        if (!CONFIRMED_RESULT.equals(processed.getResultCode())) {
            throw new ConflictException("Request was already processed with result: " + processed.getResultCode());
        }
        RoomReservation reservation = reservationRepository
                .findByRequestId(request.requestId())
                .orElseThrow(() -> new ConflictException("Reservation not found for processed request"));
        return toConfirmResponse(reservation);
    }

    public ReleaseRoomResponse replayRelease(ProcessedRequest processed, Long roomId, ReleaseRoomRequest request) {
        boolean samePayload = RELEASE_OPERATION.equals(processed.getOperation())
                && processed.getRoomId().equals(roomId)
                && processed.getBookingId().equals(request.bookingId());
        if (!samePayload) {
            throw new ConflictException("requestId already used with a different payload");
        }
        return new ReleaseRoomResponse(roomId, request.bookingId(), true);
    }

    public ConfirmAvailabilityResponse toConfirmResponse(RoomReservation reservation) {
        Room room = reservation.getRoom();
        return new ConfirmAvailabilityResponse(
                room.getId(),
                reservation.getBookingId(),
                true,
                room.getNumber(),
                room.getHotel().getId(),
                room.getHotel().getName(),
                room.getPricePerNight(),
                reservation.getStartDate(),
                reservation.getEndDate());
    }

    public ProcessedRequest processedConfirm(ConfirmAvailabilityRequest request, Long roomId) {
        ProcessedRequest processed = new ProcessedRequest();
        processed.setRequestId(request.requestId());
        processed.setOperation(CONFIRM_OPERATION);
        processed.setBookingId(request.bookingId());
        processed.setRoomId(roomId);
        processed.setResultCode(CONFIRMED_RESULT);
        processed.setStartDate(request.startDate());
        processed.setEndDate(request.endDate());
        return processed;
    }

    public ProcessedRequest processedRelease(ReleaseRoomRequest request, Long roomId) {
        ProcessedRequest processed = new ProcessedRequest();
        processed.setRequestId(request.requestId());
        processed.setOperation(RELEASE_OPERATION);
        processed.setBookingId(request.bookingId());
        processed.setRoomId(roomId);
        processed.setResultCode(RELEASED_RESULT);
        return processed;
    }
}
