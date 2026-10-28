package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 座位展示三态（文档 3.5）。bitmap 只有「不可售/可售」二态，
 * 已锁 / 已售 的区分来自 order 与 ticket 表。
 */
@Getter
public enum SeatStatus {

    AVAILABLE(0, "可售"),
    LOCKED(1, "已锁"),
    SOLD(2, "已售");

    private final int code;
    private final String desc;

    SeatStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
