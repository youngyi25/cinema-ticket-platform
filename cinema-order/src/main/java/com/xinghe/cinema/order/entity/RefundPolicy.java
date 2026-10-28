package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退票手续费阶梯（文档 6.1）。cinema_id 为 NULL 表示全局默认。
 */
@Data
@TableName("refund_policy")
public class RefundPolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cinemaId;

    /** 距开场小时数下限。 */
    private Integer hoursBefore;

    /** 手续费率，0.1000 = 10%。 */
    private BigDecimal feeRate;

    private Integer enabled;
}
