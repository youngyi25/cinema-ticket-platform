package com.xinghe.cinema.common.enums;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 日期类型（price_rule.day_type，文档 7.1）：1 平日 / 2 周末 / 3 法定节假日。
 * 节假日无法用算法推导，由配置项 cinema.pricing.holidays 提供（yyyy-MM-dd 列表）。
 */
@Getter
public enum DayType {

    WEEKDAY(1, "平日"),
    WEEKEND(2, "周末"),
    HOLIDAY(3, "法定节假日");

    private final int code;
    private final String desc;

    DayType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DayType of(LocalDate date, Set<LocalDate> holidays) {
        if (holidays != null && holidays.contains(date)) {
            return HOLIDAY;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return WEEKEND;
        }
        return WEEKDAY;
    }
}
