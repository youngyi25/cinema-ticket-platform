package com.xinghe.cinema.schedule.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排片请求。end_time 由服务端按「片长 + 影院清场时长」计算，客户端不可指定。
 */
@Data
public class CreateSessionRequest {

    @NotNull(message = "影院不能为空")
    private Long cinemaId;

    @NotNull(message = "影厅不能为空")
    private Long hallId;

    @NotNull(message = "影片不能为空")
    private Long movieId;

    @NotNull(message = "开场时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "基础票价不能为空")
    @DecimalMin(value = "0.01", message = "基础票价必须大于 0")
    private BigDecimal basePrice;

    /** 不传则使用当前最新价格版本。 */
    private Long priceVersion;
}
