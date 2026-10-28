package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 支付单状态：0 待支付 / 1 支付成功 / 2 支付失败 / 3 已关闭。
 */
@Getter
public enum PayStatus {

    WAIT_PAY(0, "待支付"),
    SUCCESS(1, "支付成功"),
    FAILED(2, "支付失败"),
    CLOSED(3, "已关闭");

    private final int code;
    private final String desc;

    PayStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
