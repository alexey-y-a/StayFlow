package ru.alexey.stayflow.hotel.dto;

import java.util.UUID;

public record ReleaseRoomResponse(Long roomId, UUID bookingId, boolean released) {
}
