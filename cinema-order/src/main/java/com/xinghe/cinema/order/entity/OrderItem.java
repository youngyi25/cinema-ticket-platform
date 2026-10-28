package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细（文档 0.1 order-service 核心表；文档 1.4 未给 DDL，此处补齐）。
 * 一行 = 一张票的座位与价格快照。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 改签时会更新为新场次。 */
    private Long sessionId;

    private Long seatId;

    private String seatLabel;

    /** 价格快照（文档 7.2）。 */
    private BigDecimal price;
}
