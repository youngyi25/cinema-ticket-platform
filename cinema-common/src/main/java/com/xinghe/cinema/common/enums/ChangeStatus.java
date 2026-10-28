package com.xinghe.cinema.common.enums;

import lombok.Getter;

/**
 * 改签记录状态（change_record.status）。
 * PENDING_PAY：差价需补款，等待用户支付，超时回滚（文档 6.3）。
 */
@Getter
public enum ChangeStatus {

    SUCCESS(1, "改签成功"),
    PENDING_PAY(2, "待补款"),
    ROLLBACK(3, "已回滚");

    private final int code;
    private final String desc;

    ChangeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
