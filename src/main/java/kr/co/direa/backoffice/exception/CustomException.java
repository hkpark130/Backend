package kr.co.direa.backoffice.exception;

import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import org.springframework.http.HttpStatus;

public class CustomException extends RuntimeException {
    private final CustomErrorCode errorCode;

    public CustomException(CustomErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(CustomErrorCode errorCode, String message) {
        super(message != null && !message.isBlank() ? message : errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(CustomErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public CustomException(CustomErrorCode errorCode, String message, Throwable cause) {
        super(message != null && !message.isBlank() ? message : errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public CustomErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
