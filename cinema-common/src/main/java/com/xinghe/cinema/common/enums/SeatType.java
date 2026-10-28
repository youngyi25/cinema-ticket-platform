package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 座位类型（seat.seat_type）：1 普通 / 2 情侣 / 3 无障碍 / 4 损坏。
 * 损坏座位（4）不可售，锁座时直接拒绝。
 */
@Getter
public enum SeatType {

    NORMAL(1, "普通"),
    COUPLE(2, "情侣"),
    ACCESSIBLE(3, "无障碍"),
    BROKEN(4, "损坏");

    private final int code;
    private final String desc;

    SeatType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SeatType of(Integer code) {
        if (code == null) {
            return NORMAL;
        }
        for (SeatType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return NORMAL;
    }
}
