package ru.alexey.stayflow.booking.dto;

import ru.alexey.stayflow.booking.model.Role;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        Role role) {
}
