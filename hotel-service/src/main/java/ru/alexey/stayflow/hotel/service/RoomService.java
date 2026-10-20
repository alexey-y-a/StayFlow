package ru.alexey.stayflow.hotel.service;

import org.springframework.data.domain.Pageable;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.RoomCreateRequest;
import ru.alexey.stayflow.hotel.dto.RoomFilter;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomUpdateRequest;

public interface RoomService {

    PageResponse<RoomResponse> findAll(RoomFilter filter, Pageable pageable);

    RoomResponse findById(Long id);

    RoomResponse create(RoomCreateRequest request);

    RoomResponse update(Long id, RoomUpdateRequest request);

    void delete(Long id);
}
