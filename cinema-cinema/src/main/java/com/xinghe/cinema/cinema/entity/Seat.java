package com.xinghe.cinema.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 座位（文档 1.4）。注意：bitmap 偏移量由 (row_no, col_no, col_count) 计算，与 id 无关。
 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hallId;

    private Integer rowNo;

    private Integer colNo;

    /** 1 普通 / 2 情侣 / 3 无障碍 / 4 损坏。 */
    private Integer seatType;

    /** 价格分区 A/B/C。 */
    private String areaCode;

    public String label() {
        return rowNo + "排" + colNo + "座";
    }
}
