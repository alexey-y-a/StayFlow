package ru.alexey.stayflow.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import ru.alexey.stayflow.booking.model.Booking;
import ru.alexey.stayflow.booking.model.BookingEvent;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;

@ActiveProfiles("test")
@DataJpaTest
class BookingRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEventRepository bookingEventRepository;

    @Test
    void savesAndFindsBookingByRequestIdAndUser() {
        User user = userRepository.save(user("alice"));
        Booking booking = bookingRepository.saveAndFlush(booking(user, "req-1"));

        assertThat(bookingRepository.findByRequestId("req-1")).contains(booking);
        assertThat(bookingRepository
                        .findByUser_Id(user.getId(), PageRequest.of(0, 10))
                        .getContent())
                .containsExactly(booking);
        assertThat(bookingRepository
                        .findByUser_IdAndStatus(user.getId(), BookingStatus.PENDING, PageRequest.of(0, 10))
                        .getContent())
                .containsExactly(booking);
    }

    @Test
    void rejectsDuplicateUsername() {
        userRepository.saveAndFlush(user("alice"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("alice")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateRequestId() {
        User user = userRepository.save(user("alice"));
        bookingRepository.saveAndFlush(booking(user, "same-request"));

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(booking(user, "same-request")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesBookingEvent() {
        User user = userRepository.save(user("alice"));
        Booking booking = bookingRepository.saveAndFlush(booking(user, "req-event"));

        BookingEvent event = new BookingEvent();
        event.setBooking(booking);
        event.setFromStatus(null);
        event.setToStatus(BookingStatus.PENDING);
        event.setReason("Booking created");
        event.setTraceId("trace-1");
        bookingEventRepository.saveAndFlush(event);

        assertThat(bookingEventRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId()))
                .hasSize(1)
                .first()
                .extracting(BookingEvent::getToStatus)
                .isEqualTo(BookingStatus.PENDING);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("{bcrypt}hash");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }

    private Booking booking(User user, String requestId) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setUser(user);
        booking.setRoomId(1L);
        booking.setHotelId(1L);
        booking.setStartDate(LocalDate.now().plusDays(10));
        booking.setEndDate(LocalDate.now().plusDays(14));
        booking.setStatus(BookingStatus.PENDING);
        booking.setRequestId(requestId);
        booking.setTotalPrice(new BigDecimal("20000.00"));
        booking.setCurrency("RUB");
        return booking;
    }
}
