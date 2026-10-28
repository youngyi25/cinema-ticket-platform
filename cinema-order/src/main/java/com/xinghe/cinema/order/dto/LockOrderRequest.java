package com.xinghe.cinema.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 锁座下单请求（POST /order/lock，文档 0.2）。
 */
@Data
public class LockOrderRequest {

    @NotNull(message = "会员不能为空")
    private Long memberId;

    @NotNull(message = "场次不能为空")
    private Long sessionId;

    @NotEmpty(message = "请选择座位")
    @Size(max = 6, message = "单笔最多 6 张")
    private List<Long> seatIds;

    /** 前端生成的请求号，用于幂等（文档 2.4）。 */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 可选优惠券。 */
    private Long couponId;
}
