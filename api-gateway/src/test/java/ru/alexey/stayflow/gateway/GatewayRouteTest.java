package ru.alexey.stayflow.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(
        properties = {
            "eureka.client.enabled=false",
            "stayflow.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "stayflow.security.jwt.issuer=https://stayflow.example"
        })
class GatewayRouteTest {

    private static final String BOOKING_ID = "11111111-1111-1111-1111-111111111111";

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void discoveryProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", WIRE_MOCK::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(post(urlEqualTo("/user/auth"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"test-token\"}")));
        WIRE_MOCK.stubFor(get(urlEqualTo("/bookings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "content": [],
                                  "page": 0,
                                  "size": 10,
                                  "totalElements": 0,
                                  "totalPages": 0,
                                  "first": true,
                                  "last": true
                                }
                                """)));
        WIRE_MOCK.stubFor(get(urlEqualTo("/booking/" + BOOKING_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + BOOKING_ID + "\"}")));
        WIRE_MOCK.stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[]}")));
        WIRE_MOCK.stubFor(get(urlEqualTo("/api/hotels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    @Test
    void routesPublicLoginToBookingService() {
        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "user",
                          "password": "Password123!"
                        }
                        """)
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/user/auth")));
    }

    @Test
    void returnsJson401ForUnauthenticatedApiCall() {
        webTestClient
                .get()
                .uri("/api/bookings")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHORIZED")
                .jsonPath("$.status")
                .isEqualTo(401);
    }

    @Test
    void routesAuthenticatedBookingHistory() {
        webTestClient
                .mutateWith(mockUser("user").roles("USER"))
                .get()
                .uri("/api/bookings")
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/bookings")));
    }

    @Test
    void routesBookingById() {
        webTestClient
                .mutateWith(mockUser("user").roles("USER"))
                .get()
                .uri("/api/bookings/" + BOOKING_ID)
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/booking/" + BOOKING_ID)));
    }

    @Test
    void routesUsersAdminPath() {
        webTestClient
                .mutateWith(mockUser("admin").roles("ADMIN"))
                .get()
                .uri("/api/users")
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/user")));
    }

    @Test
    void routesHotelRequests() {
        webTestClient
                .mutateWith(mockUser("user").roles("USER"))
                .get()
                .uri("/api/hotels")
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/api/hotels")));
    }

    @Test
    void blocksInternalHotelEndpoints() {
        webTestClient
                .post()
                .uri("/api/rooms/1/release")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND");

        webTestClient
                .get()
                .uri("/api/rooms/internal/recommend")
                .exchange()
                .expectStatus()
                .isNotFound();

        WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo("/api/rooms/1/release")));
        WIRE_MOCK.verify(0, getRequestedFor(urlEqualTo("/api/rooms/internal/recommend")));
    }

    @Test
    void removesSpoofedInternalApiKey() {
        webTestClient
                .mutateWith(mockUser("user").roles("USER"))
                .get()
                .uri("/api/hotels")
                .header("X-Internal-Api-Key", "spoofed")
                .exchange()
                .expectStatus()
                .isOk();

        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/api/hotels")).withoutHeader("X-Internal-Api-Key"));
    }
}
