package ru.alexey.stayflow.booking.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.alexey.stayflow.booking.exception.HotelClientException;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.common.error.ErrorCode;
import ru.alexey.stayflow.common.error.RemoteServiceException;
import ru.alexey.stayflow.common.web.TraceHeaders;

public class HttpHotelGateway implements HotelGateway {

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public HttpHotelGateway(
            RestClient.Builder builder,
            String baseUrl,
            String internalApiKey,
            Retry retry,
            CircuitBreaker circuitBreaker,
            TimeLimiter timeLimiter,
            ExecutorService executor) {
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
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    @Override
    public List<HotelRoomInfo> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return execute(() -> doRecommend(hotelId, startDate, endDate));
    }

    @Override
    public HotelConfirmResponse confirmAvailability(
            Long roomId, UUID bookingId, String requestId, LocalDate startDate, LocalDate endDate) {
        return execute(() -> doConfirm(roomId, bookingId, requestId, startDate, endDate));
    }

    @Override
    public void release(Long roomId, UUID bookingId, String requestId) {
        execute(() -> {
            doRelease(roomId, bookingId, requestId);
            return null;
        });
    }

    private List<HotelRoomInfo> doRecommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/api/rooms/internal/recommend");
        if (hotelId != null) {
            uri.queryParam("hotelId", hotelId);
        }
        if (startDate != null) {
            uri.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uri.queryParam("endDate", endDate);
        }
        try {
            List<HotelRoomInfo> rooms = restClient
                    .get()
                    .uri(uri.build().toUri())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<HotelRoomInfo>>() {});
            return rooms == null ? List.of() : rooms;
        } catch (HttpClientErrorException exception) {
            throw new HotelClientException("Hotel recommend rejected: " + exception.getStatusCode(), exception);
        } catch (HttpServerErrorException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_ERROR, "Hotel recommend failed: " + exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_UNAVAILABLE, "Hotel service is unavailable", exception);
        }
    }

    private HotelConfirmResponse doConfirm(
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
        } catch (HttpClientErrorException exception) {
            throw new HotelClientException("Hotel confirm rejected: " + exception.getStatusCode(), exception);
        } catch (HttpServerErrorException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_ERROR, "Hotel confirm failed: " + exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_UNAVAILABLE, "Hotel service is unavailable", exception);
        }
    }

    private void doRelease(Long roomId, UUID bookingId, String requestId) {
        try {
            restClient
                    .post()
                    .uri("/api/rooms/{id}/release", roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HotelReleaseRequest(bookingId, requestId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            throw new HotelClientException("Hotel release rejected: " + exception.getStatusCode(), exception);
        } catch (HttpServerErrorException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_ERROR, "Hotel release failed: " + exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException(
                    ErrorCode.REMOTE_SERVICE_UNAVAILABLE, "Hotel service is unavailable", exception);
        }
    }

    private <T> T execute(Supplier<T> supplier) {
        Supplier<T> decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, executor);
            try {
                return timeLimiter.executeFutureSupplier(() -> future);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new RemoteServiceException(ErrorCode.REMOTE_TIMEOUT, "Hotel service timeout", exception);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RemoteServiceException(ErrorCode.REMOTE_SERVICE_ERROR, "Hotel service call failed", cause);
            }
        }));
        return decorated.get();
    }
}
