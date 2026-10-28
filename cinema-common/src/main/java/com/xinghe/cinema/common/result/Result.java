package com.xinghe.cinema.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体。
 * code = 0 表示成功，其余为业务/系统错误码。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
