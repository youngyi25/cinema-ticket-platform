package com.xinghe.cinema.order.support;

import com.xinghe.cinema.cinema.entity.Seat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单座位字段的序列化工具。
 * order.seat_ids / seat_labels 是逗号分隔字符串（文档 1.4 的存法），改签与释放都要读写它。
 */
public final class SeatSupport {

    private SeatSupport() {
    }

    public static String joinIds(List<Long> seatIds) {
        return seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static String joinLabels(List<Seat> seats) {
        return seats.stream().map(Seat::label).collect(Collectors.joining(","));
    }

    public static List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                ids.add(Long.parseLong(t));
            }
        }
        return ids;
    }
}
