package com.xinghe.cinema.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 影厅（文档 1.4）。row_count/col_count 决定 bitmap 排布。
 */
@Data
@TableName("hall")
public class Hall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cinemaId;

    private String name;

    /** 1 普通 / 2 情侣 / 3 IMAX / 4 杜比。 */
    private Integer hallType;

    private Integer rowCount;

    private Integer colCount;

    private Integer status;

    /** 座位数（行 × 列）。 */
    public int seatCount() {
        return (rowCount == null ? 0 : rowCount) * (colCount == null ? 0 : colCount);
    }
}
