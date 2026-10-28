package com.xinghe.cinema.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券。文档 0.1 只给出表名，字段按「满减券」最小可用集设计。
 */
@Data
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private String name;

    /** 抵扣金额。 */
    private BigDecimal amount;

    /** 使用门槛（订单金额需 ≥ 该值）。 */
    private BigDecimal threshold;

    /** 0 未使用 / 1 已使用 / 2 已过期。 */
    private Integer status;

    private LocalDateTime expireAt;

    private Long usedOrderId;

    private LocalDateTime createdAt;
}
