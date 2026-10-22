package ru.alexey.stayflow.common.error;

/**
 * Возникает, когда Idempotency-Key или requestId повторно используется с другим телом запроса.
 */
public class IdempotencyConflictException extends ApiException {

    public IdempotencyConflictException(String message) {
        super(ErrorCode.IDEMPOTENCY_CONFLICT, message);
    }
}
