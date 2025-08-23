package io.hhplus.tdd.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 공용
    INTERNAL_ERROR("500", HttpStatus.INTERNAL_SERVER_ERROR, "에러가 발생했습니다."),
    BAD_REQUEST("400", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    // 포인트 도메인
    INSUFFICIENT_BALANCE("1001", HttpStatus.BAD_REQUEST, "잔고가 부족합니다."),
    INVALID_AMOUNT("1002", HttpStatus.BAD_REQUEST, "금액을 확인해 주세요.(금액은 1원 이상이어야 합니다.)");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}