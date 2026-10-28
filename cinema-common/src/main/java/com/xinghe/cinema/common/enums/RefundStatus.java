package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 退款单状态：0 处理中 / 1 退款成功 / 2 退款失败。
 */
@Getter
public enum RefundStatus {

    PROCESSING(0, "处理中"),
    SUCCESS(1, "退款成功"),
    FAILED(2, "退款失败");

    private final int code;
    private final String desc;

    RefundStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
