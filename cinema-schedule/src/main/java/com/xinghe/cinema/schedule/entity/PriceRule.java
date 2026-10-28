package com.xinghe.cinema.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 价格规则（文档 7.1）。
 * 四个维度（日类型 / 时段 / 厅型 / 分区）合成一行 multiplier，
 * 因此「最终票价 = base_price × multiplier」等价于文档公式里的多系数连乘。
 */
@Data
@TableName("price_rule")
public class PriceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long version;

    /** 1 平日 / 2 周末 / 3 法定节假日。 */
    private Integer dayType;

    /** MORNING / NOON / EVENING / MIDNIGHT。 */
    private String timeSlot;

    private Integer hallType;

    private String areaCode;

    /** 0.8000 = 8 折。 */
    private BigDecimal multiplier;
}
