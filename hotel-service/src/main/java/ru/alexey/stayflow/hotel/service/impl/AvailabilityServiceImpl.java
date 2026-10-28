package ru.alexey.stayflow.hotel.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.validation.DateRanges;
import ru.alexey.stayflow.common.web.TraceHeaders;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.model.ProcessedRequest;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomReservation;
import ru.alexey.stayflow.hotel.repository.ProcessedRequestRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;
import ru.alexey.stayflow.hotel.service.AvailabilityService;
import ru.alexey.stayflow.hotel.service.ReservationReplayService;
import ru.alexey.stayflow.hotel.service.RoomRecommendationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final ProcessedRequestRepository processedRequestRepository;
    private final RoomRecommendationService roomRecommendationService;
    private final ReservationReplayService replayService;

    @Override
    @Transactional
    public ConfirmAvailabilityResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        DateRanges.requireFutureRange(request.startDate(), request.endDate());
        MDC.put(TraceHeaders.MDC_BOOKING_ID, request.bookingId().toString());
        try {
            Optional<ProcessedRequest> existing = processedRequestRepository.findByRequestId(request.requestId());
            if (existing.isPresent()) {
                log.info("Confirm request {} already processed, replaying", request.requestId());
                return replayService.replayConfirm(existing.get(), roomId, request);
            }

            Room room = roomRepository
                    .findByIdForUpdate(roomId)
                    .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));

            Optional<ProcessedRequest> afterLock = processedRequestRepository.findByRequestId(request.requestId());
            if (afterLock.isPresent()) {
                log.info("Confirm request {} processed while waiting for lock, replaying", request.requestId());
                return replayService.replayConfirm(afterLock.get(), roomId, request);
            }

            if (!room.isAvailable()) {
                log.warn("Room {} is not operational", roomId);
                throw new ConflictException("Room is not operational: " + roomId);
            }

            if (reservationRepository.existsOverlapping(roomId, request.startDate(), request.endDate())) {
                log.warn("Room {} is already reserved for {} - {}", roomId, request.startDate(), request.endDate());
                throw new ConflictException("Room is not available for requested dates");
            }

            Optional<RoomReservation> existingByBooking = reservationRepository.findByBookingId(request.bookingId());
            if (existingByBooking.isPresent()) {
                return existingReservation(existingByBooking.get(), roomId, request);
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

            processedRequestRepository.save(replayService.processedConfirm(request, roomId));

            log.info(
                    "Availability confirmed for room {} and booking {}, dates {} - {}",
                    roomId,
                    request.bookingId(),
                    request.startDate(),
                    request.endDate());
            return replayService.toConfirmResponse(reservation);
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    @Override
    @Transactional
    public ReleaseRoomResponse releaseRoom(Long roomId, ReleaseRoomRequest request) {
        MDC.put(TraceHeaders.MDC_BOOKING_ID, request.bookingId().toString());
        try {
            Optional<ProcessedRequest> existing = processedRequestRepository.findByRequestId(request.requestId());
            if (existing.isPresent()) {
                log.info("Release request {} already processed, replaying", request.requestId());
                return replayService.replayRelease(existing.get(), roomId, request);
            }

            roomRepository
                    .findByIdForUpdate(roomId)
                    .orElseThrow(() -> new NotFoundException("Room not found: " + roomId));

            Optional<ProcessedRequest> afterLock = processedRequestRepository.findByRequestId(request.requestId());
            if (afterLock.isPresent()) {
                log.info("Release request {} processed while waiting for lock, replaying", request.requestId());
                return replayService.replayRelease(afterLock.get(), roomId, request);
            }

            reservationRepository.findByBookingId(request.bookingId()).ifPresent(reservation -> {
                if (!reservation.getRoom().getId().equals(roomId)) {
                    log.warn(
                            "Booking {} belongs to room {}",
                            request.bookingId(),
                            reservation.getRoom().getId());
                    throw new ConflictException("Booking " + request.bookingId() + " belongs to another room");
                }
                if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                    reservation.setStatus(ReservationStatus.RELEASED);
                    reservation.setReleasedAt(Instant.now());
                    reservationRepository.save(reservation);
                }
            });

            processedRequestRepository.save(replayService.processedRelease(request, roomId));

            log.info("Room {} released for booking {}", roomId, request.bookingId());
            return new ReleaseRoomResponse(roomId, request.bookingId(), true);
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return roomRecommendationService.recommend(hotelId, startDate, endDate);
    }

    private ConfirmAvailabilityResponse existingReservation(
            RoomReservation reservation, Long roomId, ConfirmAvailabilityRequest request) {
        boolean sameReservation = reservation.getRoom().getId().equals(roomId)
                && reservation.getStartDate().equals(request.startDate())
                && reservation.getEndDate().equals(request.endDate())
                && reservation.getStatus() == ReservationStatus.CONFIRMED;
        if (!sameReservation) {
            log.warn("Booking {} already has a reservation for another room or dates", request.bookingId());
            throw new ConflictException("Booking already has a reservation: " + request.bookingId());
        }
        log.info("Booking {} already confirmed for room {}, replaying", request.bookingId(), roomId);
        return replayService.toConfirmResponse(reservation);
    }
}
