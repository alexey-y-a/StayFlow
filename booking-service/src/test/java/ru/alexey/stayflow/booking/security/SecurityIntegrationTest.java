package ru.alexey.stayflow.booking.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.alexey.stayflow.booking.repository.UserRepository;

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
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotListUsers() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/user")).andExpect(status().isOk());
    }

    @Test
    void registrationIsPublic() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "publicuser",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotCreateUser() throws Exception {
        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "username": "managed",
                                  "password": "Password123!",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateUpdateAndDeleteUser() throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/user").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "username": "managed",
                                  "password": "Password123!",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("managed"))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();

        mockMvc.perform(patch("/user").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "id": %d,
                                  "role": "USER",
                                  "enabled": true
                                }
                                """.formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(delete("/user").param("id", String.valueOf(id))).andExpect(status().isNoContent());
    }
}
