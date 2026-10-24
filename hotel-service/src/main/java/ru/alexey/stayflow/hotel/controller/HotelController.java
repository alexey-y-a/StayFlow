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
import ru.alexey.stayflow.hotel.dto.HotelCreateRequest;
import ru.alexey.stayflow.hotel.dto.HotelFilter;
import ru.alexey.stayflow.hotel.dto.HotelResponse;
import ru.alexey.stayflow.hotel.dto.HotelUpdateRequest;
import ru.alexey.stayflow.hotel.service.HotelService;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Tag(name = "Hotels", description = "Hotel management")
public class HotelController {

    private final HotelService hotelService;

    @GetMapping
    @Operation(summary = "List hotels with filters and pagination")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResponse<HotelResponse> findAll(
            @ParameterObject @ModelAttribute HotelFilter filter,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return hotelService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get hotel by id")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public HotelResponse findById(@PathVariable Long id) {
        return hotelService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create hotel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody HotelCreateRequest request) {
        HotelResponse response = hotelService.create(request);
        return ResponseEntity.created(URI.create("/api/hotels/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update hotel")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelUpdateRequest request) {
        return hotelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete hotel")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }
}
