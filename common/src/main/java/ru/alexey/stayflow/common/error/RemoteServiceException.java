package ru.alexey.stayflow.common.error;

public class RemoteServiceException extends ApiException {

    public RemoteServiceException(String message) {
        super(ErrorCode.REMOTE_SERVICE_ERROR, message);
    }

    public RemoteServiceException(String message, Throwable cause) {
        super(ErrorCode.REMOTE_SERVICE_ERROR, message, cause);
    }

    public RemoteServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}