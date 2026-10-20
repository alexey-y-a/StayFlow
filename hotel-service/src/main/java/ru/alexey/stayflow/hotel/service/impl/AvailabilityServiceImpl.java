package ru.alexey.stayflow.hotel.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.BadRequestException;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.mapper.RoomMapper;
import ru.alexey.stayflow.hotel.model.ProcessedRequest;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomReservation;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.ProcessedRequestRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;
import ru.alexey.stayflow.hotel.service.AvailabilityService;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final String CONFIRM_OPERATION = "CONFIRM_AVAILABILITY";
    private static final String RELEASE_OPERATION = "RELEASE";
    private static final String CONFIRMED_RESULT = "CONFIRMED";
    private static final String RELEASED_RESULT = "RELEASED";

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final ProcessedRequestRepository processedRequestRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public ConfirmAvailabilityResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        validateDates(request.startDate(), request.endDate());

        Optional<ProcessedRequest> existing = processedRequestRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            return replayConfirm(existing.get(), roomId, request);
        }

        Room room = roomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));

        Optional<ProcessedRequest> afterLock = processedRequestRepository.findByRequestId(request.requestId());
        if (afterLock.isPresent()) {
            return replayConfirm(afterLock.get(), roomId, request);
        }

        if (!room.isAvailable()) {
            throw new ConflictException("Room is not operational: " + roomId);
        }

        if (reservationRepository.existsOverlapping(roomId, request.startDate(), request.endDate())) {
            throw new ConflictException("Room is not available for requested dates");
        }

        Optional<RoomReservation> existingByBooking = reservationRepository.findByBookingId(request.bookingId());
        if (existingByBooking.isPresent()) {
            RoomReservation reservation = existingByBooking.get();
            if (reservation.getRoom().getId().equals(roomId)
                    && reservation.getStartDate().equals(request.startDate())
                    && reservation.getEndDate().equals(request.endDate())
                    && reservation.getStatus() == ReservationStatus.CONFIRMED) {
                return toConfirmResponse(reservation);
            }
            throw new ConflictException("Booking already has a reservation: " + request.bookingId());
        }

        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(request.bookingId());
        reservation.setRequestId(request.requestId());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);

        processedRequestRepository.save(processedConfirm(request, roomId));

        return toConfirmResponse(reservation);
    }

    @Override
    @Transactional
    public ReleaseRoomResponse releaseRoom(Long roomId, ReleaseRoomRequest request) {
        Optional<ProcessedRequest> existing = processedRequestRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            return replayRelease(existing.get(), roomId, request);
        }

        roomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));

        Optional<ProcessedRequest> afterLock = processedRequestRepository.findByRequestId(request.requestId());
        if (afterLock.isPresent()) {
            return replayRelease(afterLock.get(), roomId, request);
        }

        reservationRepository.findByBookingId(request.bookingId()).ifPresent(reservation -> {
            if (!reservation.getRoom().getId().equals(roomId)) {
                throw new ConflictException("Booking " + request.bookingId() + " belongs to another room");
            }
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                reservation.setStatus(ReservationStatus.RELEASED);
                reservation.setReleasedAt(Instant.now());
                reservationRepository.save(reservation);
            }
        });

        processedRequestRepository.save(processedRelease(request, roomId));

        return new ReleaseRoomResponse(roomId, request.bookingId(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        validateOptionalDates(startDate, endDate);
        if (hotelId != null && !hotelRepository.existsById(hotelId)) {
            throw new NotFoundException("Hotel not found: " + hotelId);
        }

        List<Room> rooms;
        if (startDate == null) {
            rooms = hotelId == null
                    ? roomRepository.findByAvailableTrueOrderByTimesBookedAscIdAsc()
                    : roomRepository.findByAvailableTrueAndHotelIdOrderByTimesBookedAscIdAsc(hotelId);
        } else {
            rooms = hotelId == null
                    ? roomRepository.findRecommended(ReservationStatus.CONFIRMED, startDate, endDate)
                    : roomRepository.findRecommendedByHotelId(
                            hotelId, ReservationStatus.CONFIRMED, startDate, endDate);
        }
        return rooms.stream().map(roomMapper::toResponse).toList();
    }

    private ConfirmAvailabilityResponse replayConfirm(
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

    private ReleaseRoomResponse replayRelease(
            ProcessedRequest processed, Long roomId, ReleaseRoomRequest request) {
        boolean samePayload = RELEASE_OPERATION.equals(processed.getOperation())
                && processed.getRoomId().equals(roomId)
                && processed.getBookingId().equals(request.bookingId());
        if (!samePayload) {
            throw new ConflictException("requestId already used with a different payload");
        }
        return new ReleaseRoomResponse(roomId, request.bookingId(), true);
    }

    private ConfirmAvailabilityResponse toConfirmResponse(RoomReservation reservation) {
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

    private ProcessedRequest processedConfirm(ConfirmAvailabilityRequest request, Long roomId) {
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

    private ProcessedRequest processedRelease(ReleaseRoomRequest request, Long roomId) {
        ProcessedRequest processed = new ProcessedRequest();
        processed.setRequestId(request.requestId());
        processed.setOperation(RELEASE_OPERATION);
        processed.setBookingId(request.bookingId());
        processed.setRoomId(roomId);
        processed.setResultCode(RELEASED_RESULT);
        return processed;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start date and end date are required");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BadRequestException("Start date must be before end date");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date must not be in the past");
        }
    }

    private void validateOptionalDates(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new BadRequestException("Both startDate and endDate must be provided together");
        }
        if (startDate != null) {
            validateDates(startDate, endDate);
        }
    }
}
