package com.xinghe.cinema.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 改签结果。needPay=true 表示需要补款，前端据此跳支付。
 */
@Data
public class ChangeResult {

    private String orderNo;
    private Long changeId;
    /** 1 已生效 / 2 待补款。 */
    private Integer status;
    private BigDecimal diffAmount;
    private boolean needPay;
    private LocalDateTime payExpireAt;
    private String message;
}
