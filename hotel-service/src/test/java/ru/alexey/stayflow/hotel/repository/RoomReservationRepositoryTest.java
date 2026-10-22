package ru.alexey.stayflow.hotel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.model.ProcessedRequest;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomReservation;
import ru.alexey.stayflow.hotel.model.RoomType;

@ActiveProfiles("test")
@DataJpaTest
class RoomReservationRepositoryTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository reservationRepository;

    @Autowired
    private ProcessedRequestRepository processedRequestRepository;

    @Test
    void detectsOverlappingReservations() {
        Room room = persistRoom("101");
        reservationRepository.saveAndFlush(reservation(
                room,
                UUID.randomUUID(),
                "req-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 5)));

        assertThat(reservationRepository.existsOverlapping(
                room.getId(), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6)))
                .isTrue();
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7)))
                .isFalse();
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 1)))
                .isFalse();
    }

    @Test
    void rejectsDuplicateRoomNumberInSameHotel() {
        Hotel hotel = hotelRepository.save(hotel("Hotel"));
        roomRepository.saveAndFlush(room(hotel, "101"));

        assertThatThrownBy(() -> roomRepository.saveAndFlush(room(hotel, "101")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateProcessedRequest() {
        processedRequestRepository.saveAndFlush(processedRequest("req-1"));

        assertThatThrownBy(() -> processedRequestRepository.saveAndFlush(processedRequest("req-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsRoomForUpdate() {
        Room room = persistRoom("101");

        assertThat(roomRepository.findByIdForUpdate(room.getId())).isPresent();
    }

    private Room persistRoom(String number) {
        Hotel hotel = hotelRepository.save(hotel("Hotel"));
        return roomRepository.save(room(hotel, number));
    }

    private Hotel hotel(String name) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCity("Москва");
        hotel.setAddress("ул. Тестовая, 1");
        hotel.setRating(new BigDecimal("4.5"));
        return hotel;
    }

    private Room room(Hotel hotel, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("5000.00"));
        room.setFloor(1);
        room.setAvailable(true);
        room.setTimesBooked(0);
        return room;
    }

    private RoomReservation reservation(
            Room room, UUID bookingId, String requestId, LocalDate startDate, LocalDate endDate) {
        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(bookingId);
        reservation.setRequestId(requestId);
        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservation;
    }

    private ProcessedRequest processedRequest(String requestId) {
        ProcessedRequest request = new ProcessedRequest();
        request.setRequestId(requestId);
        request.setOperation("CONFIRM_AVAILABILITY");
        request.setBookingId(UUID.randomUUID());
        request.setRoomId(1L);
        request.setResultCode("CONFIRMED");
        return request;
    }
}