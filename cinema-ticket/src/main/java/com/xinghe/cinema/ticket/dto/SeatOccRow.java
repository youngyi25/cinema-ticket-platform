package com.xinghe.cinema.ticket.dto;

import lombok.Data;

/**
 * 重建 bitmap 用的行：场次 + 座位坐标 + 该排总列数（用于算 offset）。
 */
@Data
public class SeatOccRow {

    private Long sessionId;
    private Integer rowNo;
    private Integer colNo;
    private Integer colCount;
}
