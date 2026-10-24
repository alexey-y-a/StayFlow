package ru.alexey.stayflow.booking.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ru.alexey.stayflow.booking.exception.HotelClientException;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.common.error.ErrorCode;
import ru.alexey.stayflow.common.error.RemoteServiceException;

class HttpHotelGatewayTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = LocalDate.now().plusDays(14);
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private WireMockServer wireMock;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void callsInternalRecommendEndpoint() {
        wireMock.stubFor(get(urlPathEqualTo("/api/rooms/internal/recommend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        HttpHotelGateway gateway = gateway(retry(1), timeLimiter(1000));

        assertThat(gateway.recommend(1L, START, END)).isEmpty();
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/rooms/internal/recommend"))
                .withQueryParam("hotelId", equalTo("1"))
                .withQueryParam("startDate", equalTo(START.toString()))
                .withQueryParam("endDate", equalTo(END.toString()))
                .withHeader("X-Internal-Api-Key", equalTo("test-internal-key")));
    }

    @Test
    void retriesServerErrors() {
        wireMock.stubFor(post(urlEqualTo("/api/rooms/1/confirm-availability"))
                .willReturn(aResponse().withStatus(500)));

        HttpHotelGateway gateway = gateway(retry(3), timeLimiter(1000));

        assertThatThrownBy(() -> gateway.confirmAvailability(1L, BOOKING_ID, "req-1", START, END))
                .isInstanceOf(RemoteServiceException.class);
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/rooms/1/confirm-availability")));
    }

    @Test
    void doesNotRetryConflict() {
        wireMock.stubFor(post(urlEqualTo("/api/rooms/1/confirm-availability"))
                .willReturn(aResponse().withStatus(409)));

        HttpHotelGateway gateway = gateway(retry(3), timeLimiter(1000));

        assertThatThrownBy(() -> gateway.confirmAvailability(1L, BOOKING_ID, "req-1", START, END))
                .isInstanceOf(RoomNotAvailableException.class);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/rooms/1/confirm-availability")));
    }

    @Test
    void timesOutSlowCalls() {
        wireMock.stubFor(post(urlEqualTo("/api/rooms/1/confirm-availability"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(1500)
                        .withBody(successBody())));

        HttpHotelGateway gateway = gateway(retry(1), timeLimiter(200));

        assertThatThrownBy(() -> gateway.confirmAvailability(1L, BOOKING_ID, "req-1", START, END))
                .isInstanceOfSatisfying(
                        RemoteServiceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REMOTE_TIMEOUT));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/rooms/1/confirm-availability")));
    }

    @Test
    void returnsSuccessfulConfirmation() {
        wireMock.stubFor(post(urlEqualTo("/api/rooms/1/confirm-availability"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody())));

        HttpHotelGateway gateway = gateway(retry(3), timeLimiter(1000));

        HotelConfirmResponse response = gateway.confirmAvailability(1L, BOOKING_ID, "req-1", START, END);

        assertThat(response.available()).isTrue();
        assertThat(response.hotelId()).isEqualTo(10L);
        assertThat(response.pricePerNight()).isEqualByComparingTo("5000.00");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/rooms/1/confirm-availability")));
    }

    private HttpHotelGateway gateway(Retry retry, TimeLimiter timeLimiter) {
        return new HttpHotelGateway(
                RestClient.builder(),
                wireMock.baseUrl(),
                "test-internal-key",
                retry,
                CircuitBreaker.ofDefaults("test"),
                timeLimiter,
                executor);
    }

    private Retry retry(int maxAttempts) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(RemoteServiceException.class)
                .ignoreExceptions(RoomNotAvailableException.class, HotelClientException.class)
                .build();
        return Retry.of("test", config);
    }

    private TimeLimiter timeLimiter(long timeoutMillis) {
        return TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMillis))
                .cancelRunningFuture(true)
                .build());
    }

    private String successBody() {
        return """
                {
                  "roomId": 1,
                  "bookingId": "11111111-1111-1111-1111-111111111111",
                  "available": true,
                  "roomNumber": "101",
                  "hotelId": 10,
                  "hotelName": "Hotel",
                  "pricePerNight": 5000.00,
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-05"
                }
                """;
    }
}
