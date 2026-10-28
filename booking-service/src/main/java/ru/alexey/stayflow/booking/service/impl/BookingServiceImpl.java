package ru.alexey.stayflow.booking.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.client.HotelRoomInfo;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.booking.mapper.BookingMapper;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.booking.service.BookingPriceCalculator;
import ru.alexey.stayflow.booking.service.BookingRecoveryService;
import ru.alexey.stayflow.booking.service.BookingRequestIds;
import ru.alexey.stayflow.booking.service.BookingRequestValidator;
import ru.alexey.stayflow.booking.service.BookingService;
import ru.alexey.stayflow.booking.service.BookingStateService;
import ru.alexey.stayflow.common.error.BadRequestException;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.ForbiddenException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.error.RemoteServiceException;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.common.web.TraceHeaders;

/**
 * Оркестрация саги бронирования: создание, подтверждение, отмена и чтение.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final HotelGateway hotelGateway;
    private final BookingMapper bookingMapper;
    private final BookingRequestValidator requestValidator;
    private final BookingPriceCalculator priceCalculator;
    private final BookingStateService stateService;
    private final BookingRecoveryService recoveryService;

    @Override
    public BookingResponse create(String username, String requestId, CreateBookingRequest request) {
        requestValidator.validateRequestId(requestId);
        requestValidator.validateDates(request.startDate(), request.endDate());

        Optional<Booking> existing = bookingRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            requestValidator.verifyIdempotentPayload(existing.get(), request);
            return stateService.getInternal(existing.get());
        }

        List<Candidate> candidates = resolveCandidates(request);
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));

        Booking pending;
        try {
            Candidate first = candidates.getFirst();
            pending = stateService.createPending(
                    user.getId(), first.roomId(), first.hotelId(), request.startDate(), request.endDate(), requestId);
        } catch (DataIntegrityViolationException exception) {
            Booking duplicate = bookingRepository.findByRequestId(requestId).orElseThrow(() -> exception);
            requestValidator.verifyIdempotentPayload(duplicate, request);
            return stateService.getInternal(duplicate);
        }

        UUID bookingId = pending.getId();
        MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId.toString());
        try {
            log.info("Booking {} created in status PENDING", bookingId);
            return confirm(bookingId, requestId, request, candidates);
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    private BookingResponse confirm(
            UUID bookingId, String requestId, CreateBookingRequest request, List<Candidate> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            if (index > 0) {
                stateService.updateRoom(bookingId, candidate.roomId(), candidate.hotelId());
            }
            try {
                HotelConfirmResponse confirmation = hotelGateway.confirmAvailability(
                        candidate.roomId(),
                        bookingId,
                        BookingRequestIds.confirm(requestId, candidate.roomId()),
                        request.startDate(),
                        request.endDate());
                BigDecimal totalPrice = priceCalculator.totalPrice(
                        confirmation.pricePerNight(), request.startDate(), request.endDate());
                BookingResponse response = stateService.markConfirmed(bookingId, confirmation.hotelId(), totalPrice);
                log.info("Booking {} confirmed for room {}", bookingId, candidate.roomId());
                return response;
            } catch (RoomNotAvailableException exception) {
                if (!request.autoSelect() || index == candidates.size() - 1) {
                    stateService.markCancelled(bookingId, "Room is not available");
                    log.warn("Booking {} cancelled, room {} is not available", bookingId, candidate.roomId());
                    throw new ConflictException("Room is not available for requested dates");
                }
                log.info("Room {} is not available, trying next candidate", candidate.roomId());
            } catch (RemoteServiceException exception) {
                stateService.markCancelled(bookingId, "Hotel service error");
                boolean released = releaseQuietly(candidate.roomId(), bookingId, requestId);
                stateService.markCompensation(
                        bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
                log.warn(
                        "Booking {} cancelled after remote error, compensation {}",
                        bookingId,
                        released ? "DONE" : "PENDING");
                throw exception;
            }
        }
        throw new ConflictException("No available rooms");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> history(String username, BookingStatus status, Pageable pageable) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        Page<Booking> page = status == null
                ? bookingRepository.findByUser_Id(user.getId(), pageable)
                : bookingRepository.findByUser_IdAndStatus(user.getId(), status, pageable);
        return PageResponse.from(page.map(bookingMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(String username, UUID bookingId, boolean admin) {
        Booking booking = stateService.findBooking(bookingId);
        requireAccess(booking, username, admin);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public void cancel(String username, UUID bookingId, boolean admin) {
        Booking booking = stateService.findBooking(bookingId);
        requireAccess(booking, username, admin);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId.toString());
        try {
            boolean released = releaseQuietly(booking.getRoomId(), bookingId, booking.getRequestId());
            stateService.markCancelled(bookingId, "Cancelled by user");
            stateService.markCompensation(bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
            log.info("Booking {} cancelled by {}, compensation {}", bookingId, username, released ? "DONE" : "PENDING");
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    @Override
    public void recover() {
        recoveryService.recover();
    }

    private List<Candidate> resolveCandidates(CreateBookingRequest request) {
        if (request.autoSelect()) {
            List<HotelRoomInfo> rooms =
                    hotelGateway.recommend(request.hotelId(), request.startDate(), request.endDate());
            if (rooms == null || rooms.isEmpty()) {
                throw new ConflictException("No available rooms for requested dates");
            }
            return rooms.stream()
                    .limit(3)
                    .map(room -> new Candidate(room.id(), room.hotelId()))
                    .toList();
        }
        if (request.roomId() == null) {
            throw new BadRequestException("roomId is required when autoSelect is false");
        }
        return List.of(new Candidate(request.roomId(), request.hotelId()));
    }

    private void requireAccess(Booking booking, String username, boolean admin) {
        if (!admin && !booking.getUser().getUsername().equals(username)) {
            throw new ForbiddenException("Access to booking denied");
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

    private record Candidate(Long roomId, Long hotelId) {}
}
