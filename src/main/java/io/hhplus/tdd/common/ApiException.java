package io.hhplus.tdd.common;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public String code() { return errorCode.code(); }
    public int httpStatus() { return errorCode.status().value(); }
    public ErrorCode errorCode() { return errorCode; }
}