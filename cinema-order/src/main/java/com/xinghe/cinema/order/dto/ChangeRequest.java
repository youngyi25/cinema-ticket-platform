package com.xinghe.cinema.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 改签请求（文档 6.2）。
 */
@Data
public class ChangeRequest {

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotNull(message = "新场次不能为空")
    private Long newSessionId;

    @NotEmpty(message = "请选择新座位")
    @Size(max = 6, message = "单笔最多 6 张")
    private List<Long> newSeatIds;
}
