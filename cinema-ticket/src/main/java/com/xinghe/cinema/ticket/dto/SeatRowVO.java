package com.xinghe.cinema.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatRowVO {

    private Integer rowNo;
    private List<SeatItemVO> seats;
}
