package ru.alexey.stayflow.booking.dto;

import java.time.Instant;
import ru.alexey.stayflow.booking.model.Role;

public record UserResponse(
        Long id, String username, Role role, boolean enabled, Instant createdAt, Instant updatedAt) {}
