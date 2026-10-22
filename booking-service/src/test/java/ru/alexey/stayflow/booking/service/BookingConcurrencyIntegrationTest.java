package ru.alexey.stayflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingEventRepository;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.common.error.ConflictException;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    private static final LocalDate START = LocalDate.now().plusDays(20);
    private static final LocalDate END = LocalDate.now().plusDays(24);
    private static final int THREADS = 6;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEventRepository bookingEventRepository;

    @MockitoBean
    private HotelGateway hotelGateway;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        bookingEventRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("concurrent"));
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Параллельные запросы с одним Idempotency-Key создают одно бронирование")
    void parallelRequestsWithSameKeyCreateSingleBooking() throws Exception {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L));

        List<BookingResponse> responses =
                runInParallel(index -> bookingService.create("concurrent", "same-key", request(1L, 10L)));

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        assertThat(responses).hasSize(THREADS);
        assertThat(responses).extracting(BookingResponse::id).containsOnly(bookings.getFirst().getId());
        assertThat(bookings.getFirst().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookingEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Параллельные брони одного номера: подтверждается только одна")
    void parallelBookingsOfSameRoomConfirmOnlyOne() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenAnswer(invocation -> {
                    if (calls.getAndIncrement() == 0) {
                        return confirmation(1L, 10L);
                    }
                    throw new RoomNotAvailableException("busy");
                });

        List<BookingStatus> outcomes = runInParallel(index -> {
            try {
                return bookingService.create("concurrent", "key-" + index, request(1L, 10L)).status();
            } catch (ConflictException exception) {
                return BookingStatus.CANCELLED;
            }
        });

        assertThat(outcomes).filteredOn(status -> status == BookingStatus.CONFIRMED).hasSize(1);
        assertThat(outcomes).filteredOn(status -> status == BookingStatus.CANCELLED).hasSize(THREADS - 1);
        assertThat(bookingRepository.count()).isEqualTo(THREADS);
        assertThat(bookingRepository.findAll())
                .extracting(Booking::getRequestId)
                .doesNotHaveDuplicates();
        assertThat(bookingRepository.findAll())
                .filteredOn(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .hasSize(1);
    }

    private <T> List<T> runInParallel(IntFunction<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            int taskIndex = index;
            futures.add(executor.submit(() -> {
                start.await();
                return task.apply(taskIndex);
            }));
        }
        start.countDown();

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }

    private CreateBookingRequest request(Long roomId, Long hotelId) {
        return new CreateBookingRequest(roomId, hotelId, START, END, false);
    }

    private HotelConfirmResponse confirmation(Long roomId, Long hotelId) {
        return new HotelConfirmResponse(
                roomId, null, true, "101", hotelId, "Hotel", new BigDecimal("5000.00"), START, END);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("hash");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }
}
