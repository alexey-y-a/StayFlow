package ru.alexey.stayflow.hotel.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.alexey.stayflow.common.web.TraceHeaders;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security")
@TestPropertySource(
        properties = {
            "eureka.client.enabled=false",
            "stayflow.seed.enabled=false",
            "stayflow.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "stayflow.security.jwt.issuer=https://stayflow.example",
            "stayflow.security.internal-api-key=test-internal-key"
        })
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void cleanDatabase() {
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCanReadHotels() throws Exception {
        mockMvc.perform(get("/api/hotels")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Secure Hotel",
                                  "city": "Москва",
                                  "address": "ул. Безопасная, 1",
                                  "description": "Тест",
                                  "rating": 4.5
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void internalEndpointRequiresInternalKey() throws Exception {
        mockMvc.perform(post("/api/rooms/1/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": "11111111-1111-1111-1111-111111111111",
                                  "requestId": "req-internal",
                                  "startDate": "2026-11-01",
                                  "endDate": "2026-11-05"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void internalEndpointAcceptsValidKey() throws Exception {
        mockMvc.perform(post("/api/rooms/999/confirm-availability")
                        .header(TraceHeaders.INTERNAL_API_KEY, "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": "11111111-1111-1111-1111-111111111111",
                                  "requestId": "req-internal",
                                  "startDate": "2026-11-01",
                                  "endDate": "2026-11-05"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Secure Hotel",
                                  "city": "Москва",
                                  "address": "ул. Безопасная, 1",
                                  "description": "Тест",
                                  "rating": 4.5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Secure Hotel"));
    }
}
