package ru.alexey.stayflow.booking.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.client.HotelRoomInfo;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.booking.mapper.BookingMapper;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingEvent;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingEventRepository;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.booking.service.BookingService;
import ru.alexey.stayflow.common.error.BadRequestException;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.ForbiddenException;
import ru.alexey.stayflow.common.error.IdempotencyConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.error.RemoteServiceException;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.common.web.TraceHeaders;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingEventRepository bookingEventRepository;
    private final HotelGateway hotelGateway;
    private final BookingMapper bookingMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${stayflow.booking.recovery.pending-timeout:PT2M}")
    private Duration pendingTimeout;

    @Override
    public BookingResponse create(String username, String requestId, CreateBookingRequest request) {
        validateRequestId(requestId);
        validateDates(request.startDate(), request.endDate());

        Optional<Booking> existing = bookingRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            verifyIdempotentPayload(existing.get(), request);
            return getInternal(existing.get());
        }

        List<Candidate> candidates = resolveCandidates(request);
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));

        Booking pending;
        try {
            pending = transactionTemplate.execute(status -> createPending(user, candidates.get(0), request, requestId));
        } catch (DataIntegrityViolationException exception) {
            Booking duplicate = bookingRepository.findByRequestId(requestId).orElseThrow(() -> exception);
            verifyIdempotentPayload(duplicate, request);
            return getInternal(duplicate);
        }

        UUID bookingId = pending.getId();
        MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId.toString());
        log.info("Booking {} created in status PENDING", bookingId);

        try {
            for (int index = 0; index < candidates.size(); index++) {
                Candidate candidate = candidates.get(index);
                if (index > 0) {
                    updateRoom(bookingId, candidate);
                }
                try {
                    HotelConfirmResponse confirmation = hotelGateway.confirmAvailability(
                            candidate.roomId(),
                            bookingId,
                            confirmRequestId(requestId, candidate.roomId()),
                            request.startDate(),
                            request.endDate());
                    BigDecimal totalPrice =
                            totalPrice(confirmation.pricePerNight(), request.startDate(), request.endDate());
                    BookingResponse response = markConfirmed(bookingId, confirmation.hotelId(), totalPrice);
                    log.info("Booking {} confirmed", bookingId);
                    return response;
                } catch (RoomNotAvailableException exception) {
                    if (!request.autoSelect() || index == candidates.size() - 1) {
                        markCancelled(bookingId, "Room is not available");
                        throw new ConflictException("Room is not available for requested dates");
                    }
                    log.info("Room {} is not available, trying next candidate", candidate.roomId());
                } catch (RemoteServiceException exception) {
                    markCancelled(bookingId, "Hotel service error");
                    boolean released = releaseQuietly(candidate.roomId(), bookingId, releaseRequestId(requestId));
                    markCompensation(bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
                    throw exception;
                }
            }
            throw new ConflictException("No available rooms");
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
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
        Booking booking = findBooking(bookingId);
        requireAccess(booking, username, admin);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public void cancel(String username, UUID bookingId, boolean admin) {
        Booking booking = findBooking(bookingId);
        requireAccess(booking, username, admin);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        MDC.put(TraceHeaders.MDC_BOOKING_ID, bookingId.toString());
        try {
            boolean released = releaseQuietly(booking.getRoomId(), bookingId, releaseRequestId(booking.getRequestId()));
            markCancelled(bookingId, "Cancelled by user");
            markCompensation(bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
            log.info("Booking {} cancelled", bookingId);
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

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
                    confirmRequestId(booking.getRequestId(), booking.getRoomId()),
                    booking.getStartDate(),
                    booking.getEndDate());
            BigDecimal totalPrice =
                    totalPrice(confirmation.pricePerNight(), booking.getStartDate(), booking.getEndDate());
            markConfirmed(bookingId, confirmation.hotelId(), totalPrice);
            log.info("Recovery confirmed booking {}", bookingId);
        } catch (RoomNotAvailableException exception) {
            markCancelled(bookingId, "Room is not available");
            boolean released = releaseQuietly(booking.getRoomId(), bookingId, releaseRequestId(booking.getRequestId()));
            markCompensation(bookingId, released ? CompensationStatus.DONE : CompensationStatus.PENDING);
            log.info("Recovery cancelled booking {}", bookingId);
        } catch (RemoteServiceException exception) {
            log.warn("Recovery postponed for booking {}: {}", bookingId, exception.getMessage());
        } finally {
            MDC.remove(TraceHeaders.MDC_BOOKING_ID);
        }
    }

    private void retryCompensation(Booking booking) {
        boolean released =
                releaseQuietly(booking.getRoomId(), booking.getId(), releaseRequestId(booking.getRequestId()));
        if (released) {
            markCompensation(booking.getId(), CompensationStatus.DONE);
            log.info("Compensation completed for booking {}", booking.getId());
        } else {
            log.warn("Compensation still pending for booking {}", booking.getId());
        }
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

    private Booking createPending(User user, Candidate candidate, CreateBookingRequest request, String requestId) {
        return transactionTemplate.execute(status -> {
            User managedUser = userRepository
                    .findById(user.getId())
                    .orElseThrow(() -> new NotFoundException("User not found: " + user.getId()));
            Booking booking = new Booking();
            booking.setId(UUID.randomUUID());
            booking.setUser(managedUser);
            booking.setRoomId(candidate.roomId());
            booking.setHotelId(candidate.hotelId());
            booking.setStartDate(request.startDate());
            booking.setEndDate(request.endDate());
            booking.setStatus(BookingStatus.PENDING);
            booking.setRequestId(requestId);
            booking.setCurrency("RUB");
            bookingRepository.save(booking);
            bookingEventRepository.save(event(booking, null, BookingStatus.PENDING, "Booking created"));
            return booking;
        });
    }

    private void updateRoom(UUID bookingId, Candidate candidate) {
        transactionTemplate.executeWithoutResult(status -> {
            Booking booking = bookingRepository
                    .findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
            booking.setRoomId(candidate.roomId());
            booking.setHotelId(candidate.hotelId());
            bookingRepository.save(booking);
        });
    }

    private BookingResponse markConfirmed(UUID bookingId, Long hotelId, BigDecimal totalPrice) {
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

    private void markCancelled(UUID bookingId, String reason) {
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

    private Booking findBooking(UUID bookingId) {
        return bookingRepository
                .findByIdWithUser(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
    }

    private BookingResponse getInternal(Booking booking) {
        return transactionTemplate.execute(status -> {
            Booking fresh = findBooking(booking.getId());
            return bookingMapper.toResponse(fresh);
        });
    }

    private void requireAccess(Booking booking, String username, boolean admin) {
        if (!admin && !booking.getUser().getUsername().equals(username)) {
            throw new ForbiddenException("Access to booking denied");
        }
    }

    private boolean releaseQuietly(Long roomId, UUID bookingId, String requestId) {
        try {
            hotelGateway.release(roomId, bookingId, requestId);
            return true;
        } catch (RemoteServiceException exception) {
            log.error("Compensation release failed for booking {} and room {}", bookingId, roomId, exception);
            return false;
        }
    }

    private void markCompensation(UUID bookingId, CompensationStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            Booking booking = bookingRepository
                    .findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
            booking.setCompensationStatus(status);
            bookingRepository.save(booking);
        });
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

    private void verifyIdempotentPayload(Booking existing, CreateBookingRequest request) {
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

    private void validateRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        if (requestId.length() > 80) {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters");
        }
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

    private BigDecimal totalPrice(BigDecimal pricePerNight, LocalDate startDate, LocalDate endDate) {
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        return pricePerNight.multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
    }

    private String confirmRequestId(String requestId, Long roomId) {
        return UUID.nameUUIDFromBytes((requestId + ":confirm:" + roomId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private String releaseRequestId(String requestId) {
        return UUID.nameUUIDFromBytes((requestId + ":release").getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private record Candidate(Long roomId, Long hotelId) {}
}
