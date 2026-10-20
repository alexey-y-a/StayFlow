package ru.alexey.stayflow.hotel.service;

import org.springframework.data.domain.Pageable;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.HotelCreateRequest;
import ru.alexey.stayflow.hotel.dto.HotelFilter;
import ru.alexey.stayflow.hotel.dto.HotelResponse;
import ru.alexey.stayflow.hotel.dto.HotelUpdateRequest;

public interface HotelService {

    PageResponse<HotelResponse> findAll(HotelFilter filter, Pageable pageable);

    HotelResponse findById(Long id);

    HotelResponse create(HotelCreateRequest request);

    HotelResponse update(Long id, HotelUpdateRequest request);

    void delete(Long id);
}
