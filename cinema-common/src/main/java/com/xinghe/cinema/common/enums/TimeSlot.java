package com.xinghe.cinema.common.enums;

import lombok.Getter;

import java.time.LocalTime;

/**
 * 时段（price_rule.time_slot，文档 7.1）。
 */
@Getter
public enum TimeSlot {

    MIDNIGHT("MIDNIGHT", "午夜场", LocalTime.MIDNIGHT, LocalTime.of(6, 0)),
    MORNING("MORNING", "上午场", LocalTime.of(6, 0), LocalTime.of(12, 0)),
    NOON("NOON", "下午场", LocalTime.of(12, 0), LocalTime.of(18, 0)),
    EVENING("EVENING", "晚场", LocalTime.of(18, 0), LocalTime.MIDNIGHT);

    private final String code;
    private final String desc;
    private final LocalTime from;
    private final LocalTime to;

    TimeSlot(String code, String desc, LocalTime from, LocalTime to) {
        this.code = code;
        this.desc = desc;
        this.from = from;
        this.to = to;
    }

    /** 按开场时间归入时段：00:00-05:59 午夜 / 06:00-11:59 上午 / 12:00-17:59 下午 / 18:00-23:59 晚场。 */
    public static TimeSlot of(LocalTime time) {
        int hour = time.getHour();
        if (hour < 6) {
            return MIDNIGHT;
        }
        if (hour < 12) {
            return MORNING;
        }
        if (hour < 18) {
            return NOON;
        }
        return EVENING;
    }
}
