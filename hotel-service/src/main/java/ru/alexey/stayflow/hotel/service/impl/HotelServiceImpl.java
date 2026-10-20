package ru.alexey.stayflow.hotel.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.HotelCreateRequest;
import ru.alexey.stayflow.hotel.dto.HotelFilter;
import ru.alexey.stayflow.hotel.dto.HotelResponse;
import ru.alexey.stayflow.hotel.dto.HotelUpdateRequest;
import ru.alexey.stayflow.hotel.mapper.HotelMapper;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.specification.HotelSpecifications;
import ru.alexey.stayflow.hotel.service.HotelService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

    @Override
    public PageResponse<HotelResponse> findAll(HotelFilter filter, Pageable pageable) {
        Page<Hotel> page = hotelRepository.findAll(HotelSpecifications.byFilter(filter), pageable);
        return PageResponse.from(page.map(hotelMapper::toResponse));
    }

    @Override
    public HotelResponse findById(Long id) {
        return hotelMapper.toResponse(findHotel(id));
    }

    @Override
    @Transactional
    public HotelResponse create(HotelCreateRequest request) {
        if (hotelRepository.existsByNameAndCity(request.name(), request.city())) {
            throw new ConflictException("Hotel already exists: " + request.name() + ", " + request.city());
        }
        Hotel hotel = hotelMapper.toEntity(request);
        return hotelMapper.toResponse(hotelRepository.save(hotel));
    }

    @Override
    @Transactional
    public HotelResponse update(Long id, HotelUpdateRequest request) {
        Hotel hotel = findHotel(id);
        boolean nameOrCityChanged =
                !hotel.getName().equals(request.name()) || !hotel.getCity().equals(request.city());
        if (nameOrCityChanged && hotelRepository.existsByNameAndCity(request.name(), request.city())) {
            throw new ConflictException("Hotel already exists: " + request.name() + ", " + request.city());
        }
        hotelMapper.update(request, hotel);
        return hotelMapper.toResponse(hotelRepository.save(hotel));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Hotel hotel = findHotel(id);
        if (roomRepository.countByHotelId(id) > 0) {
            throw new ConflictException("Hotel has rooms and cannot be deleted: " + id);
        }
        hotelRepository.delete(hotel);
    }

    private Hotel findHotel(Long id) {
        return hotelRepository.findById(id).orElseThrow(() -> new NotFoundException("Hotel not found: " + id));
    }
}
