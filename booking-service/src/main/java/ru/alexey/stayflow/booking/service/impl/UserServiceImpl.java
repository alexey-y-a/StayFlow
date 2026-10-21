package ru.alexey.stayflow.booking.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.booking.dto.CreateUserRequest;
import ru.alexey.stayflow.booking.dto.UpdateUserRequest;
import ru.alexey.stayflow.booking.dto.UserResponse;
import ru.alexey.stayflow.booking.mapper.UserMapper;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.booking.service.UserService;
import ru.alexey.stayflow.common.error.BadRequestException;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.web.PageResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return PageResponse.from(page.map(userMapper::toResponse));
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists: " + request.username());
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse update(UpdateUserRequest request) {
        User user = findUser(request.id());

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new ConflictException("Username already exists: " + request.username());
            }
            user.setUsername(request.username());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void delete(Long id, String currentUsername) {
        User user = findUser(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new BadRequestException("You cannot delete yourself");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ConflictException("Cannot delete the last administrator");
        }
        userRepository.delete(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
