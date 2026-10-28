package com.xinghe.cinema.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 出票入参：座位 + 价格快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatPrice {

    private Long seatId;

    private BigDecimal price;
}
