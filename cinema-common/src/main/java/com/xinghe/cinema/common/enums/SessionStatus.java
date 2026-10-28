package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 场次状态（session.status）：1 可售 / 0 停售 / -1 已取消。
 */
@Getter
public enum SessionStatus {

    ON_SALE(1, "可售"),
    STOPPED(0, "停售"),
    CANCELED(-1, "已取消");

    private final int code;
    private final String desc;

    SessionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
