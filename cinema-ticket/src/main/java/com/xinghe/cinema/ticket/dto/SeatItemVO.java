package com.xinghe.cinema.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 座位图上的单个座位。
 * status：0 可售 / 1 已锁 / 2 已售（文档 3.5）。
 */
@Data
public class SeatItemVO {

    private Long seatId;
    private Integer colNo;
    private Integer status;
    private String area;
    private Integer seatType;
    /** 该座位实际票价（分区定价，文档 7.3）。 */
    private BigDecimal price;
}
