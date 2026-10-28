package com.xinghe.cinema.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（文档 0.1 payment-service 核心表）。
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付流水号，渠道唯一。 */
    private String payNo;

    private Long orderId;

    private String orderNo;

    private BigDecimal amount;

    /** MOCK / ALIPAY / WECHAT。 */
    private String channel;

    /** 0 待支付 / 1 支付成功 / 2 支付失败 / 3 已关闭。 */
    private Integer status;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
