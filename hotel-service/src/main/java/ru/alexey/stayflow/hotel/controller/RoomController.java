package ru.alexey.stayflow.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.RoomCreateRequest;
import ru.alexey.stayflow.hotel.dto.RoomFilter;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomUpdateRequest;
import ru.alexey.stayflow.hotel.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @Operation(summary = "List rooms with filters and pagination")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResponse<RoomResponse> findAll(
            @ParameterObject @ModelAttribute RoomFilter filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return roomService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get room by id")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public RoomResponse findById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create room")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomCreateRequest request) {
        RoomResponse response = roomService.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update room")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete room")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}
