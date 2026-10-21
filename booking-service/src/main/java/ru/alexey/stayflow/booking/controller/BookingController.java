package ru.alexey.stayflow.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.service.BookingService;
import ru.alexey.stayflow.common.web.PageResponse;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
@Tag(name = "Bookings", description = "Booking creation, history and cancellation")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/booking")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking with manual room selection or autoSelect")
    public BookingResponse create(
            @RequestHeader("Idempotency-Key") String requestId,
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication) {
        return bookingService.create(authentication.getName(), requestId, request);
    }

    @GetMapping("/bookings")
    @Operation(summary = "Get current user booking history")
    public PageResponse<BookingResponse> history(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return bookingService.history(authentication.getName(), status, pageable);
    }

    @GetMapping("/booking/{id}")
    @Operation(summary = "Get booking by id")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return bookingService.get(authentication.getName(), id, isAdmin(authentication));
    }

    @DeleteMapping("/booking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a booking")
    public void cancel(@PathVariable UUID id, Authentication authentication) {
        bookingService.cancel(authentication.getName(), id, isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
