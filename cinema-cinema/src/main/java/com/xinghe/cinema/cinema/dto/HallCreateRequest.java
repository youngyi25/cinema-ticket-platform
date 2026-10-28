package com.xinghe.cinema.cinema.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 建厅请求。rowCount/colCount 上限 64：单排一座一 long 的技术边界（文档 3.4）。
 */
@Data
public class HallCreateRequest {

    @NotNull(message = "影院不能为空")
    private Long cinemaId;

    @NotBlank(message = "影厅名称不能为空")
    private String name;

    /** 1 普通 / 2 情侣 / 3 IMAX / 4 杜比。 */
    private Integer hallType = 1;

    @NotNull(message = "行数不能为空")
    @Min(value = 1, message = "行数至少 1")
    @Max(value = 40, message = "行数最多 40")
    private Integer rowCount;

    @NotNull(message = "列数不能为空")
    @Min(value = 1, message = "列数至少 1")
    @Max(value = 64, message = "列数最多 64（单 word 上限）")
    private Integer colCount;
}
