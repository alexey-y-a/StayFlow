package ru.alexey.stayflow.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HotelServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void createsHotelAndRoomAndBlocksHotelDeletion() throws Exception {
        MvcResult hotelResult = mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test Hotel",
                                  "city": "Москва",
                                  "address": "ул. Тестовая, 1",
                                  "description": "Тестовый отель",
                                  "rating": 4.5
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode hotelJson = objectMapper.readTree(hotelResult.getResponse().getContentAsString());
        long hotelId = hotelJson.get("id").asLong();

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "101",
                                  "type": "DOUBLE",
                                  "capacity": 2,
                                  "pricePerNight": 5000.00,
                                  "floor": 1,
                                  "available": true,
                                  "description": "Тестовый номер"
                                }
                                """.formatted(hotelId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(hotelId));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Hotel"));

        mockMvc.perform(get("/api/rooms").param("hotelId", String.valueOf(hotelId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hotelName").value("Test Hotel"));

        mockMvc.perform(delete("/api/hotels/" + hotelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
