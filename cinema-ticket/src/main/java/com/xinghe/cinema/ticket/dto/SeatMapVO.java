package com.xinghe.cinema.ticket.dto;

import lombok.Data;

import java.util.List;

/**
 * 座位图返回体（文档 3.5）。
 * recommend 为连座推荐：每个元素是一组「列号」，与 recommendRows 同下标对应排号。
 */
@Data
public class SeatMapVO {

    private Long sessionId;
    private Long hallId;
    private Integer rowCount;
    private Integer colCount;

    private List<SeatRowVO> rows;

    /** 推荐连座，如 [[5,6,7],[6,7,8]]。 */
    private List<List<Integer>> recommend;

    /** 与 recommend 一一对应的排号。 */
    private List<Integer> recommendRows;
}
