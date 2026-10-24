package ru.alexey.stayflow.common.web;

public final class TraceHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String BOOKING_ID = "X-Booking-Id";
    public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_BOOKING_ID = "bookingId";
    public static final String MDC_USER_ID = "userId";

    private TraceHeaders() {}
}
