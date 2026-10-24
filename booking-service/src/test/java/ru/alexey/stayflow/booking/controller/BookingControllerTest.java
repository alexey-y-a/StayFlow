package ru.alexey.stayflow.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.security.SecurityConfig;
import ru.alexey.stayflow.booking.service.BookingService;
import ru.alexey.stayflow.common.config.CommonWebConfig;
import ru.alexey.stayflow.common.security.JwtConfig;
import ru.alexey.stayflow.common.web.PageResponse;

@WebMvcTest(BookingController.class)
@Import({CommonWebConfig.class, SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = "stayflow.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@WithMockUser(username = "user", roles = "USER")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private final BookingResponse response = new BookingResponse(
            UUID.randomUUID(),
            1L,
            "user",
            1L,
            10L,
            LocalDate.now().plusDays(10),
            LocalDate.now().plusDays(14),
            BookingStatus.CONFIRMED,
            new BigDecimal("20000.00"),
            "RUB",
            "req-1",
            Instant.now(),
            Instant.now());

    @Test
    void createsBooking() throws Exception {
        when(bookingService.create(eq("user"), eq("req-1"), any(CreateBookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/booking")
                        .header("Idempotency-Key", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomId": 1,
                                  "hotelId": 10,
                                  "startDate": "2026-11-01",
                                  "endDate": "2026-11-05",
                                  "autoSelect": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(1));
    }

    @Test
    void returnsValidationErrorForInvalidBooking() throws Exception {
        mockMvc.perform(post("/booking")
                        .header("Idempotency-Key", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsBookingHistory() throws Exception {
        when(bookingService.history(eq("user"), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(response), 0, 10, 1, 1, true, true, null));

        mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()));
    }

    @Test
    void cancelsBooking() throws Exception {
        mockMvc.perform(delete("/booking/" + response.id())).andExpect(status().isNoContent());
    }
}
