package ru.alexey.stayflow.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.hotel.dto.OccupancyStatsResponse;
import ru.alexey.stayflow.hotel.service.OccupancyService;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Tag(name = "Hotel statistics", description = "Occupancy and load statistics")
public class HotelStatisticsController {

    private final OccupancyService occupancyService;

    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get hotel occupancy statistics")
    public OccupancyStatsResponse statistics(@PathVariable Long id) {
        return occupancyService.getHotelStatistics(id);
    }
}
