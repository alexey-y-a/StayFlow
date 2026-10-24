package ru.alexey.stayflow.booking.client;

import java.util.UUID;

public record HotelReleaseRequest(UUID bookingId, String requestId) {}
