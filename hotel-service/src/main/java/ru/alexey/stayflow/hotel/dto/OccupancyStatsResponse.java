package ru.alexey.stayflow.hotel.dto;

import java.util.List;

public record OccupancyStatsResponse(
        Long hotelId,
        String hotelName,
        long totalRooms,
        long availableRooms,
        long occupiedRooms,
        long activeReservations,
        double occupancyRate,
        long totalTimesBooked,
        double averageTimesBooked,
        List<RoomLoadResponse> topRooms) {
}
