package ru.alexey.stayflow.hotel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.alexey.stayflow.common.config.CommonWebConfig;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.RoomCreateRequest;
import ru.alexey.stayflow.hotel.dto.RoomFilter;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.model.RoomType;
import ru.alexey.stayflow.hotel.service.RoomService;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(RoomController.class)
@Import(CommonWebConfig.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    private final RoomResponse roomResponse = new RoomResponse(
            10L,
            1L,
            "Grand Stay",
            "101",
            RoomType.DOUBLE,
            2,
            new BigDecimal("6500.00"),
            1,
            true,
            4,
            "Стандартный номер",
            Instant.now(),
            Instant.now());

    @Test
    void returnsPageOfRooms() throws Exception {
        when(roomService.findAll(any(RoomFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(roomResponse), 0, 10, 1, 1, true, true, null));

        mockMvc.perform(get("/api/rooms").param("hotelId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value("101"))
                .andExpect(jsonPath("$.content[0].hotelName").value("Grand Stay"));
    }

    @Test
    void returnsValidationErrorForInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsRoom() throws Exception {
        when(roomService.create(any(RoomCreateRequest.class))).thenReturn(roomResponse);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": 1,
                                  "number": "101",
                                  "type": "DOUBLE",
                                  "capacity": 2,
                                  "pricePerNight": 6500.00,
                                  "floor": 1,
                                  "available": true,
                                  "description": "Стандартный номер"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rooms/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void deletesRoom() throws Exception {
        mockMvc.perform(delete("/api/rooms/10")).andExpect(status().isNoContent());
    }
}
