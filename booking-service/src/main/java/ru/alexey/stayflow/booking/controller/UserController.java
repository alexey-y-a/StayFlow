package ru.alexey.stayflow.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.alexey.stayflow.booking.dto.CreateUserRequest;
import ru.alexey.stayflow.booking.dto.UpdateUserRequest;
import ru.alexey.stayflow.booking.dto.UserResponse;
import ru.alexey.stayflow.booking.service.UserService;
import ru.alexey.stayflow.common.web.PageResponse;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User administration", description = "Administrative user management")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users")
    public PageResponse<UserResponse> findAll(
            @PageableDefault(size = 10, sort = "username") Pageable pageable) {
        return userService.findAll(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping
    @Operation(summary = "Update a user")
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
        return userService.update(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    public void delete(@RequestParam Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
    }
}
