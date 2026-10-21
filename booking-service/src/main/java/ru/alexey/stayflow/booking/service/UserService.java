package ru.alexey.stayflow.booking.service;

import org.springframework.data.domain.Pageable;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.booking.dto.CreateUserRequest;
import ru.alexey.stayflow.booking.dto.UpdateUserRequest;
import ru.alexey.stayflow.booking.dto.UserResponse;

public interface UserService {

    PageResponse<UserResponse> findAll(Pageable pageable);

    UserResponse create(CreateUserRequest request);

    UserResponse update(UpdateUserRequest request);

    void delete(Long id, String currentUsername);
}
