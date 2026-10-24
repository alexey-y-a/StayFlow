package ru.alexey.stayflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.alexey.stayflow.booking.client.HotelConfirmResponse;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.client.HotelRoomInfo;
import ru.alexey.stayflow.booking.dto.BookingResponse;
import ru.alexey.stayflow.booking.dto.CreateBookingRequest;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.booking.model.BookingStatus;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;
import ru.alexey.stayflow.booking.repository.BookingEventRepository;
import ru.alexey.stayflow.booking.repository.BookingRepository;
import ru.alexey.stayflow.booking.repository.UserRepository;
import ru.alexey.stayflow.common.error.ConflictException;
import ru.alexey.stayflow.common.error.ForbiddenException;
import ru.alexey.stayflow.common.error.IdempotencyConflictException;
import ru.alexey.stayflow.common.error.RemoteServiceException;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceIntegrationTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = LocalDate.now().plusDays(14);

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

    private User user;

    @BeforeEach
    void setUp() {
        bookingEventRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(user("user"));
    }

    @Test
    @DisplayName("Создание брони подтверждает номер и рассчитывает стоимость")
    void createsConfirmedBooking() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));

        BookingResponse response = bookingService.create("user", "req-1", request(1L, 10L, false));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.totalPrice()).isEqualByComparingTo("20000.00");
        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.hotelId()).isEqualTo(10L);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Повтор с тем же Idempotency-Key возвращает существующее бронирование")
    void isIdempotentByRequestId() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));

        BookingResponse first = bookingService.create("user", "same-request", request(1L, 10L, false));
        BookingResponse second = bookingService.create("user", "same-request", request(1L, 10L, false));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingEventRepository.count()).isEqualTo(2);
        verify(hotelGateway, times(1)).confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END));
    }

    @Test
    @DisplayName("Повтор Idempotency-Key с другим телом отклоняется")
    void rejectsSameRequestIdWithDifferentPayload() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));
        bookingService.create("user", "duplicate-key", request(1L, 10L, false));

        assertThatThrownBy(() -> bookingService.create("user", "duplicate-key", request(2L, 10L, false)))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different payload");
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingRepository.findAll().getFirst().getRoomId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Занятый номер: бронь отменяется, клиент получает 409")
    void cancelsBookingWhenRoomIsNotAvailable() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenThrow(new RoomNotAvailableException("busy"));

        assertThatThrownBy(() -> bookingService.create("user", "conflict", request(1L, 10L, false)))
                .isInstanceOf(ConflictException.class);

        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingRepository.findAll().getFirst().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Автоподбор берёт следующий номер после конфликта")
    void autoSelectTriesNextCandidateAfterConflict() {
        when(hotelGateway.recommend(10L, START, END))
                .thenReturn(List.of(
                        new HotelRoomInfo(1L, 10L, "Hotel", "101", new BigDecimal("5000.00"), 5),
                        new HotelRoomInfo(2L, 10L, "Hotel", "102", new BigDecimal("6000.00"), 1)));
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenThrow(new RoomNotAvailableException("busy"));
        when(hotelGateway.confirmAvailability(eq(2L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(2L, 10L, "6000.00"));

        BookingResponse response = bookingService.create("user", "auto-select", request(null, 10L, true));

        assertThat(response.roomId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.totalPrice()).isEqualByComparingTo("24000.00");
        verify(hotelGateway, times(2)).confirmAvailability(any(), any(UUID.class), anyString(), eq(START), eq(END));
    }

    @Test
    @DisplayName("История содержит только брони текущего пользователя")
    void returnsHistoryOnlyForCurrentUser() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));
        bookingService.create("user", "history", request(1L, 10L, false));

        assertThat(bookingService.history("user", null, PageRequest.of(0, 10)).content())
                .hasSize(1);
        assertThat(bookingService
                        .history("user", BookingStatus.CONFIRMED, PageRequest.of(0, 10))
                        .content())
                .hasSize(1);
    }

    @Test
    @DisplayName("Доступ к чужому бронированию запрещён")
    void deniesAccessToAnotherUsersBooking() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));
        BookingResponse response = bookingService.create("user", "owner", request(1L, 10L, false));

        assertThatThrownBy(() -> bookingService.get("other", response.id(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Отмена подтверждённой брони освобождает слот")
    void cancelsConfirmedBookingAndReleasesRoom() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenReturn(confirmation(1L, 10L, "5000.00"));
        BookingResponse response = bookingService.create("user", "cancel", request(1L, 10L, false));

        bookingService.cancel("user", response.id(), false);

        assertThat(bookingRepository.findById(response.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingEventRepository.count()).isEqualTo(3);
        verify(hotelGateway).release(eq(1L), eq(response.id()), anyString());
    }

    @Test
    @DisplayName("Ошибка Hotel: отмена и компенсация release")
    void cancelsBookingAndReleasesRoomOnRemoteError() {
        when(hotelGateway.confirmAvailability(eq(1L), any(UUID.class), anyString(), eq(START), eq(END)))
                .thenThrow(new RemoteServiceException("down", new RuntimeException("connection refused")));

        assertThatThrownBy(() -> bookingService.create("user", "remote-error", request(1L, 10L, false)))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(bookingRepository.findAll().getFirst().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(hotelGateway).release(eq(1L), any(UUID.class), anyString());
    }

    private CreateBookingRequest request(Long roomId, Long hotelId, boolean autoSelect) {
        return new CreateBookingRequest(roomId, hotelId, START, END, autoSelect);
    }

    private HotelConfirmResponse confirmation(Long roomId, Long hotelId, String price) {
        return new HotelConfirmResponse(roomId, null, true, "101", hotelId, "Hotel", new BigDecimal(price), START, END);
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
