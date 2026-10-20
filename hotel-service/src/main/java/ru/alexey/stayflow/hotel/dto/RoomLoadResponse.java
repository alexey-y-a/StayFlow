package ru.alexey.stayflow.hotel.dto;

public record RoomLoadResponse(
        Long roomId,
        String number,
        long timesBooked,
        long activeReservations) {
}
