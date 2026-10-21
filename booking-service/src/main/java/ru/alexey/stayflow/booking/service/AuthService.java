package ru.alexey.stayflow.booking.service;

import ru.alexey.stayflow.booking.dto.AuthRequest;
import ru.alexey.stayflow.booking.dto.AuthResponse;
import ru.alexey.stayflow.booking.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(AuthRequest request);
}
