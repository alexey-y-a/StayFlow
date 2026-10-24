package ru.alexey.stayflow.booking.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.booking.dto.AuthRequest;
import ru.alexey.stayflow.booking.dto.AuthResponse;
import ru.alexey.stayflow.booking.dto.RegisterRequest;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.booking.service.AuthService;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.ForbiddenException;
import ru.alexey.stayflow.common.error.UnauthorizedException;
import ru.alexey.stayflow.common.security.JwtProperties;
import ru.alexey.stayflow.common.security.JwtTokenService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists: " + request.username());
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        userRepository.save(user);
        return response(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        User user = userRepository
                .findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        if (!user.isEnabled()) {
            throw new ForbiddenException("User is disabled");
        }
        return response(user);
    }

    private AuthResponse response(User user) {
        String result = createToken(user);
        return new AuthResponse(
                result, "Bearer", jwtProperties.ttl().toSeconds(), user.getId(), user.getUsername(), user.getRole());
    }

    private String createToken(User user) {
        return jwtTokenService.issue(
                user.getId(),
                user.getUsername(),
                List.of("ROLE_" + user.getRole().name()));
    }
}
