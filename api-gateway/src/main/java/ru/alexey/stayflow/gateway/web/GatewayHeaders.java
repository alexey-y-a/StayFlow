package ru.alexey.stayflow.gateway.web;

public final class GatewayHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String BOOKING_ID = "X-Booking-Id";
    public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    private GatewayHeaders() {}
}
