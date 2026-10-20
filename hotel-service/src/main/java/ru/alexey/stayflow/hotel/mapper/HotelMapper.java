package ru.alexey.stayflow.hotel.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import ru.alexey.stayflow.hotel.dto.HotelCreateRequest;
import ru.alexey.stayflow.hotel.dto.HotelResponse;
import ru.alexey.stayflow.hotel.dto.HotelUpdateRequest;
import ru.alexey.stayflow.hotel.model.Hotel;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Hotel toEntity(HotelCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(HotelUpdateRequest request, @MappingTarget Hotel hotel);

    HotelResponse toResponse(Hotel hotel);
}
