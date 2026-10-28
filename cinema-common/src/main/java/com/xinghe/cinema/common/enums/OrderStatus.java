package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 订单状态机（文档 6.4）。
 * 10 待支付 → 20 已支付 → 30 已出票 → 40 已观影
 * 10 → 90 已取消；20/30 → 50 退票中 → 60 已退票；30 → 70 已改签
 */
@Getter
public enum OrderStatus {

    WAIT_PAY(10, "待支付"),
    PAID(20, "已支付"),
    ISSUED(30, "已出票"),
    WATCHED(40, "已观影"),
    REFUNDING(50, "退票中"),
    REFUNDED(60, "已退票"),
    CHANGED(70, "已改签"),
    CANCELED(90, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
