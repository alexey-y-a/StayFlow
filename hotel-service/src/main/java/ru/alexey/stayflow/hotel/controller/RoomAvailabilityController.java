package ru.alexey.stayflow.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.service.AvailabilityService;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Room availability", description = "Availability confirmation, release and recommendations")
public class RoomAvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/{id}/confirm-availability")
    @Operation(summary = "Confirm room availability and create a reservation (internal)")
    public ConfirmAvailabilityResponse confirmAvailability(
            @PathVariable Long id, @Valid @RequestBody ConfirmAvailabilityRequest request) {
        return availabilityService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release room reservation (internal compensation)")
    public ReleaseRoomResponse release(@PathVariable Long id, @Valid @RequestBody ReleaseRoomRequest request) {
        return availabilityService.releaseRoom(id, request);
    }

    @GetMapping("/internal/recommend")
    @Operation(summary = "Recommend available rooms for internal service calls")
    public List<RoomResponse> recommendInternal(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityService.recommend(hotelId, startDate, endDate);
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Recommend available rooms sorted by times booked")
    public List<RoomResponse> recommend(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityService.recommend(hotelId, startDate, endDate);
    }
}
