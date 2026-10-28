package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退票申请记录（文档 0.1 order-service 核心表 refund_record）。
 * 与 payment.refund 的分工：本表记录「业务侧退票与手续费」，refund 表记录「资金侧退款流水」。
 */
@Data
@TableName("refund_record")
public class RefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 申请退款金额（实付金额）。 */
    private BigDecimal applyAmount;

    /** 手续费率，如 0.1000。 */
    private BigDecimal feeRate;

    /** 手续费。 */
    private BigDecimal fee;

    /** 实退金额。 */
    private BigDecimal refundAmount;

    private String reason;

    /** 0 申请中 / 1 退款成功 / 2 退款失败。 */
    private Integer status;

    private LocalDateTime createdAt;
}
