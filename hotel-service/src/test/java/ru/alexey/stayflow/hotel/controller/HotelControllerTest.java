package ru.alexey.stayflow.hotel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.alexey.stayflow.common.config.CommonWebConfig;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.HotelCreateRequest;
import ru.alexey.stayflow.hotel.dto.HotelFilter;
import ru.alexey.stayflow.hotel.dto.HotelResponse;
import ru.alexey.stayflow.hotel.service.HotelService;

@WebMvcTest(HotelController.class)
@Import(CommonWebConfig.class)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService hotelService;

    private final HotelResponse hotelResponse = new HotelResponse(
            1L,
            "Grand Stay",
            "Москва",
            "ул. Тверская, 1",
            "Отель в центре",
            new BigDecimal("4.8"),
            Instant.now(),
            Instant.now());

    @Test
    void returnsPageOfHotels() throws Exception {
        when(hotelService.findAll(any(HotelFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(hotelResponse), 0, 10, 1, 1, true, true, null));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Grand Stay"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void returnsNotFoundForMissingHotel() throws Exception {
        when(hotelService.findById(99L)).thenThrow(new NotFoundException("Hotel not found: 99"));

        mockMvc.perform(get("/api/hotels/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsValidationErrorForInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/hotels").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsHotel() throws Exception {
        when(hotelService.create(any(HotelCreateRequest.class))).thenReturn(hotelResponse);

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Grand Stay",
                                  "city": "Москва",
                                  "address": "ул. Тверская, 1",
                                  "description": "Отель в центре",
                                  "rating": 4.8
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/hotels/1"))
                .andExpect(jsonPath("$.id").value(1));
    }
}
