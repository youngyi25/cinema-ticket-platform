package com.xinghe.cinema.common.exception;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 统一转换为 Result.fail。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(String message) {
        return new BizException(message);
    }

    public int getCode() {
        return code;
    }
}
