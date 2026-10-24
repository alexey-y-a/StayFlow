package ru.alexey.stayflow.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.alexey.stayflow.booking.model.Role;

public record UpdateUserRequest(
        @NotNull Long id,
        @Size(min = 3, max = 50) String username,
        @Size(min = 8, max = 100) String password,
        Role role,
        Boolean enabled) {}
