package ru.alexey.stayflow.booking.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.model.Booking;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    BookingResponse toResponse(Booking booking);
}
