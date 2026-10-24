package ru.alexey.stayflow.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityRequest;
import ru.alexey.stayflow.hotel.dto.ConfirmAvailabilityResponse;
import ru.alexey.stayflow.hotel.dto.OccupancyStatsResponse;
import ru.alexey.stayflow.hotel.dto.ReleaseRoomRequest;
import ru.alexey.stayflow.hotel.dto.RoomResponse;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.model.ReservationStatus;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomReservation;
import ru.alexey.stayflow.hotel.model.RoomType;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.ProcessedRequestRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;
import ru.alexey.stayflow.hotel.repository.RoomReservationRepository;

@SpringBootTest
@ActiveProfiles("test")
class AvailabilityServiceIntegrationTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = LocalDate.now().plusDays(14);

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository reservationRepository;

    @Autowired
    private ProcessedRequestRepository processedRequestRepository;

    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        processedRequestRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        hotel = hotelRepository.save(hotel("Test Hotel"));
        room = roomRepository.save(room(hotel, "101", 3));
    }

    @Test
    @DisplayName("Подтверждение создаёт бронь слота и увеличивает timesBooked")
    void confirmCreatesReservationAndIncrementsTimesBooked() {
        UUID bookingId = UUID.randomUUID();

        ConfirmAvailabilityResponse response = availabilityService.confirmAvailability(
                room.getId(), request(bookingId, "confirm-" + bookingId, START, END));

        assertThat(response.available()).isTrue();
        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.roomId()).isEqualTo(room.getId());
        assertThat(response.pricePerNight()).isEqualByComparingTo("5000.00");
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(processedRequestRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Повтор confirm с тем же requestId не создаёт вторую бронь")
    void confirmIsIdempotentByRequestId() {
        UUID bookingId = UUID.randomUUID();
        ConfirmAvailabilityRequest request = request(bookingId, "same-" + bookingId, START, END);

        ConfirmAvailabilityResponse first = availabilityService.confirmAvailability(room.getId(), request);
        ConfirmAvailabilityResponse second = availabilityService.confirmAvailability(room.getId(), request);

        assertThat(second.bookingId()).isEqualTo(first.bookingId());
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(processedRequestRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Пересечение дат отклоняется с кодом 409")
    void overlappingBookingIsRejected() {
        UUID firstBooking = UUID.randomUUID();
        availabilityService.confirmAvailability(
                room.getId(), request(firstBooking, "first-" + firstBooking, START, END));

        UUID secondBooking = UUID.randomUUID();
        assertThatThrownBy(() -> availabilityService.confirmAvailability(
                        room.getId(),
                        request(secondBooking, "second-" + secondBooking, START.plusDays(2), END.plusDays(2))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Release освобождает слот и идемпотентен")
    void releaseMarksReservationReleasedAndIsIdempotent() {
        UUID bookingId = UUID.randomUUID();
        availabilityService.confirmAvailability(room.getId(), request(bookingId, "confirm-" + bookingId, START, END));
        ReleaseRoomRequest release = new ReleaseRoomRequest(bookingId, "release-" + bookingId);

        availabilityService.releaseRoom(room.getId(), release);
        availabilityService.releaseRoom(room.getId(), release);

        RoomReservation reservation =
                reservationRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isNotNull();
        assertThat(processedRequestRepository.count()).isEqualTo(2);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Рекомендации сортируют по timesBooked и исключают занятые")
    void recommendSortsByTimesBookedAndExcludesOccupiedRooms() {
        Room cheap = roomRepository.save(room(hotel, "102", 0));

        List<RoomResponse> withoutDates = availabilityService.recommend(hotel.getId(), null, null);
        assertThat(withoutDates).extracting(RoomResponse::id).startsWith(cheap.getId(), room.getId());

        UUID bookingId = UUID.randomUUID();
        availabilityService.confirmAvailability(
                cheap.getId(), request(bookingId, "recommend-" + bookingId, START, END));

        List<RoomResponse> withDates = availabilityService.recommend(hotel.getId(), START, END);
        assertThat(withDates).extracting(RoomResponse::id).containsExactly(room.getId());
    }

    @Test
    @DisplayName("Статистика считает загрузку и topRooms")
    void statisticsCountsOccupancy() {
        Room secondRoom = roomRepository.save(room(hotel, "102", 0));
        UUID bookingId = UUID.randomUUID();
        availabilityService.confirmAvailability(room.getId(), request(bookingId, "stats-" + bookingId, START, END));

        OccupancyStatsResponse stats = occupancyService.getHotelStatistics(hotel.getId());

        assertThat(stats.totalRooms()).isEqualTo(2);
        assertThat(stats.availableRooms()).isEqualTo(2);
        assertThat(stats.occupiedRooms()).isEqualTo(1);
        assertThat(stats.activeReservations()).isEqualTo(1);
        assertThat(stats.occupancyRate()).isEqualTo(50.0);
        assertThat(stats.totalTimesBooked()).isEqualTo(4);
        assertThat(stats.averageTimesBooked()).isEqualTo(2.0);
        assertThat(stats.topRooms())
                .extracting(room -> room.roomId())
                .containsExactly(room.getId(), secondRoom.getId());
    }

    @Test
    @DisplayName("Параллельные подтверждения разных броней: проходит только одно")
    void concurrentConfirmationsAllowOnlyOneBooking() throws Exception {
        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        UUID bookingId = UUID.randomUUID();
                        availabilityService.confirmAvailability(
                                room.getId(), request(bookingId, "concurrent-" + bookingId, START, END));
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                };
                futures.add(executor.submit(task));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(processedRequestRepository.count()).isEqualTo(1);
            assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                    .isEqualTo(4);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Параллельная повторная доставка confirm не создаёт вторую бронь")
    void concurrentDuplicateDeliveryCreatesSingleReservation() throws Exception {
        UUID bookingId = UUID.randomUUID();
        int attempts = 6;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConfirmAvailabilityResponse>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < attempts; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return availabilityService.confirmAvailability(
                            room.getId(), request(bookingId, "duplicate-" + bookingId, START, END));
                }));
            }

            start.countDown();
            for (Future<ConfirmAvailabilityResponse> future : futures) {
                assertThat(future.get(15, TimeUnit.SECONDS).available()).isTrue();
            }

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(processedRequestRepository.count()).isEqualTo(1);
            assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked())
                    .isEqualTo(4);
        } finally {
            executor.shutdownNow();
        }
    }

    private ConfirmAvailabilityRequest request(
            UUID bookingId, String requestId, LocalDate startDate, LocalDate endDate) {
        return new ConfirmAvailabilityRequest(bookingId, requestId, startDate, endDate);
    }

    private Hotel hotel(String name) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCity("Москва");
        hotel.setAddress("ул. Тестовая, 1");
        hotel.setDescription("Тестовый отель");
        hotel.setRating(new BigDecimal("4.5"));
        return hotel;
    }

    private Room room(Hotel hotel, String number, long timesBooked) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("5000.00"));
        room.setFloor(1);
        room.setAvailable(true);
        room.setTimesBooked(timesBooked);
        room.setDescription("Тестовый номер");
        return room;
    }
}
