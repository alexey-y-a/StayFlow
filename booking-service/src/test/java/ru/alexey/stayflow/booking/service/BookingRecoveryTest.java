package ru.alexey.stayflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.CompensationStatus;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingEventRepository;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.common.error.RemoteServiceException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "stayflow.booking.recovery.pending-timeout=PT0S")
class BookingRecoveryTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = LocalDate.now().plusDays(14);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEventRepository bookingEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private HotelGateway hotelGateway;

    private User user;

    @BeforeEach
    void setUp() {
        bookingEventRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(user("recovery"));
    }

    @Test
    void recoveryConfirmsStalePendingBooking() {
        Booking booking = bookingRepository.saveAndFlush(booking(BookingStatus.PENDING, CompensationStatus.NONE));
        makeCreatedAtOld(booking.getId());
        when(hotelGateway.confirmAvailability(eq(1L), eq(booking.getId()), anyString(), eq(START), eq(END)))
                .thenReturn(new HotelConfirmResponse(
                        1L, booking.getId(), true, "101", 10L, "Hotel", new BigDecimal("5000.00"), START, END));

        bookingService.recover();

        Booking recovered = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(recovered.getTotalPrice()).isEqualByComparingTo("20000.00");
        assertThat(bookingEventRepository.count()).isEqualTo(1);
    }

    @Test
    void recoveryCancelsPendingBookingWhenRoomIsBusy() {
        Booking booking = bookingRepository.saveAndFlush(booking(BookingStatus.PENDING, CompensationStatus.NONE));
        makeCreatedAtOld(booking.getId());
        when(hotelGateway.confirmAvailability(eq(1L), eq(booking.getId()), anyString(), eq(START), eq(END)))
                .thenThrow(new ru.alexey.stayflow.booking.exception.RoomNotAvailableException("busy"));
        doNothing().when(hotelGateway).release(eq(1L), eq(booking.getId()), anyString());

        bookingService.recover();

        Booking recovered = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(recovered.getCompensationStatus()).isEqualTo(CompensationStatus.DONE);
    }

    @Test
    void recoveryRetriesPendingCompensation() {
        Booking booking = bookingRepository.saveAndFlush(booking(BookingStatus.CANCELLED, CompensationStatus.PENDING));
        doNothing().when(hotelGateway).release(eq(1L), eq(booking.getId()), anyString());

        bookingService.recover();

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCompensationStatus())
                .isEqualTo(CompensationStatus.DONE);
    }

    @Test
    void recoveryKeepsCompensationPendingWhenReleaseFails() {
        Booking booking = bookingRepository.saveAndFlush(booking(BookingStatus.CANCELLED, CompensationStatus.PENDING));
        doThrow(new RemoteServiceException("down", new RuntimeException("connection refused")))
                .when(hotelGateway)
                .release(eq(1L), eq(booking.getId()), anyString());

        bookingService.recover();

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCompensationStatus())
                .isEqualTo(CompensationStatus.PENDING);
    }

    private void makeCreatedAtOld(UUID bookingId) {
        jdbcTemplate.update(
                "update bookings set created_at = ? where id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                bookingId);
    }

    private Booking booking(BookingStatus status, CompensationStatus compensationStatus) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setUser(user);
        booking.setRoomId(1L);
        booking.setHotelId(10L);
        booking.setStartDate(START);
        booking.setEndDate(END);
        booking.setStatus(status);
        booking.setCompensationStatus(compensationStatus);
        booking.setRequestId("recovery-" + UUID.randomUUID());
        booking.setCurrency("RUB");
        return booking;
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
