package ru.alexey.stayflow.common.validation;

import java.time.LocalDate;
import ru.alexey.stayflow.common.error.BadRequestException;

/**
 * Общие проверки интервалов дат, чтобы правила не дублировались в сервисах.
 */
public final class DateRanges {

    private DateRanges() {}

    public static void requireFutureRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start date and end date are required");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BadRequestException("Start date must be before end date");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date must not be in the past");
        }
    }

    public static void requireOptionalFutureRange(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new BadRequestException("Both startDate and endDate must be provided together");
        }
        if (startDate != null) {
            requireFutureRange(startDate, endDate);
        }
    }
}
