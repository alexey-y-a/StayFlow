package ru.alexey.stayflow.hotel.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.NotFoundException;
import ru.alexey.stayflow.common.web.PageResponse;
import ru.alexey.stayflow.hotel.dto.RoomCreateRequest;
import ru.alexey.stayflow.hotel.dto.RoomFilter;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.dto.RoomUpdateRequest;
import ru.alexey.stayflow.hotel.mapper.RoomMapper;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;
import ru.alexey.stayflow.hotel.repository.specification.RoomSpecifications;
import ru.alexey.stayflow.hotel.service.RoomService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final RoomMapper roomMapper;

    @Override
    public PageResponse<RoomResponse> findAll(RoomFilter filter, Pageable pageable) {
        if (filter.getAvailable() == null) {
            filter.setAvailable(true);
        }
        Page<Room> page = roomRepository.findAll(RoomSpecifications.byFilter(filter), pageable);
        return PageResponse.from(page.map(roomMapper::toResponse));
    }

    @Override
    public RoomResponse findById(Long id) {
        return roomMapper.toResponse(findRoom(id));
    }

    @Override
    @Transactional
    public RoomResponse create(RoomCreateRequest request) {
        Hotel hotel = hotelRepository
                .findById(request.hotelId())
                .orElseThrow(() -> new NotFoundException("Hotel not found: " + request.hotelId()));
        if (roomRepository.findByHotelIdAndNumber(request.hotelId(), request.number()).isPresent()) {
            throw new ConflictException("Room number already exists in hotel: " + request.number());
        }
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        room.setTimesBooked(0);
        room.setAvailable(request.available() == null || request.available());
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public RoomResponse update(Long id, RoomUpdateRequest request) {
        Room room = findRoom(id);
        Long hotelId = room.getHotel().getId();
        if (!room.getNumber().equals(request.number())
                && roomRepository.findByHotelIdAndNumber(hotelId, request.number()).isPresent()) {
            throw new ConflictException("Room number already exists in hotel: " + request.number());
        }
        roomMapper.update(request, room);
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Room room = findRoom(id);
        if (roomReservationRepository.existsByRoomId(id)) {
            throw new ConflictException("Room has reservations and cannot be deleted: " + id);
        }
        roomRepository.delete(room);
    }

    private Room findRoom(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> new NotFoundException("Room not found: " + id));
    }
}
