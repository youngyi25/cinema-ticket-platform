package com.xinghe.cinema.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单（文档 0.1 payment-service 核心表）。
 */
@Data
@TableName("refund")
public class Refund {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    /** 实际退款金额。 */
    private BigDecimal amount;

    /** 手续费（退票场景）。 */
    private BigDecimal fee;

    private String reason;

    /** 0 处理中 / 1 退款成功 / 2 退款失败。 */
    private Integer status;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;
}
