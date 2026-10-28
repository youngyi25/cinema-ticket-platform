package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 厅型（hall.hall_type / price_rule.hall_type）：1 普通 / 2 情侣 / 3 IMAX / 4 杜比。
 */
@Getter
public enum HallType {

    NORMAL(1, "普通厅"),
    COUPLE(2, "情侣厅"),
    IMAX(3, "IMAX厅"),
    DOLBY(4, "杜比厅");

    private final int code;
    private final String desc;

    HallType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static HallType of(Integer code) {
        if (code != null) {
            for (HallType t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
        }
        return NORMAL;
    }
}
