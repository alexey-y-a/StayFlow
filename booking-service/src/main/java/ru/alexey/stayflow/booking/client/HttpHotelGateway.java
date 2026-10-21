package ru.alexey.stayflow.booking.client;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.common.error.ErrorCode;
import ru.alexey.stayflow.common.error.RemoteServiceException;
import ru.alexey.stayflow.common.web.TraceHeaders;

public class HttpHotelGateway implements HotelGateway {

    private final RestClient restClient;

    public HttpHotelGateway(RestClient.Builder builder, String baseUrl, String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(TraceHeaders.INTERNAL_API_KEY, internalApiKey)
                .requestInterceptor((request, body, execution) -> {
                    String traceId = MDC.get(TraceHeaders.MDC_TRACE_ID);
                    if (traceId != null) {
                        request.getHeaders().set(TraceHeaders.REQUEST_ID, traceId);
                    }
                    String bookingId = MDC.get(TraceHeaders.MDC_BOOKING_ID);
                    if (bookingId != null) {
                        request.getHeaders().set(TraceHeaders.BOOKING_ID, bookingId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public List<HotelRoomInfo> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/api/rooms/recommend");
        if (hotelId != null) {
            uri.queryParam("hotelId", hotelId);
        }
        if (startDate != null) {
            uri.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uri.queryParam("endDate", endDate);
        }
        return restClient
                .get()
                .uri(uri.build().toUri())
                .retrieve()
                .body(new ParameterizedTypeReference<List<HotelRoomInfo>>() {});
    }

    @Override
    public HotelConfirmResponse confirmAvailability(
            Long roomId, UUID bookingId, String requestId, LocalDate startDate, LocalDate endDate) {
        try {
            return restClient
                    .post()
                    .uri("/api/rooms/{id}/confirm-availability", roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HotelConfirmRequest(bookingId, requestId, startDate, endDate))
                    .retrieve()
                    .body(HotelConfirmResponse.class);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new RoomNotAvailableException("Room is not available: " + roomId);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_ERROR,
                    "Hotel service error: " + exception.getStatusCode(),
                    exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_UNAVAILABLE, "Hotel service is unavailable", exception);
        }
    }

    @Override
    public void release(Long roomId, UUID bookingId, String requestId) {
        try {
            restClient
                    .post()
                    .uri("/api/rooms/{id}/release", roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HotelReleaseRequest(bookingId, requestId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_ERROR,
                    "Hotel release failed: " + exception.getStatusCode(),
                    exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_UNAVAILABLE, "Hotel service is unavailable", exception);
        }
    }
}
