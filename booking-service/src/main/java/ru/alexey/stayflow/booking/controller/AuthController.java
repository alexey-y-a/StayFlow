package ru.alexey.stayflow.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.booking.dto.AuthRequest;
import ru.alexey.stayflow.booking.dto.AuthResponse;
import ru.alexey.stayflow.booking.dto.RegisterRequest;
import ru.alexey.stayflow.booking.service.AuthService;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration and JWT login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a user and issue a JWT")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth")
    @Operation(summary = "Authenticate a user and issue a JWT")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }
}
