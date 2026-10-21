package ru.alexey.stayflow.booking.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.alexey.stayflow.booking.service.BookingService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "stayflow.booking.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BookingRecoveryJob {

    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "${stayflow.booking.recovery.interval:PT30S}")
    public void recover() {
        try {
            bookingService.recover();
        } catch (Exception exception) {
            log.error("Booking recovery job failed", exception);
        }
    }
}
