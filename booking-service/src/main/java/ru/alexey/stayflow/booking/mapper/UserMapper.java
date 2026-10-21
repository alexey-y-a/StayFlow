package ru.alexey.stayflow.booking.mapper;

import org.mapstruct.Mapper;
import ru.alexey.stayflow.booking.dto.UserResponse;
import ru.alexey.stayflow.booking.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
