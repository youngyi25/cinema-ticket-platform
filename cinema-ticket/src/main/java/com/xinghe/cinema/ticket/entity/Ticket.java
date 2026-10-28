package com.xinghe.cinema.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已售票据（文档 1.4）。仅支付成功后写入。
 * uk_session_seat 是最后一道防线：同一场次同一座位只能出一张票。
 */
@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long sessionId;

    private Long seatId;

    /** 价格快照，不随调价变化（文档 7.2）。 */
    private BigDecimal price;

    /** 取票码。 */
    private String verifyCode;

    /** 0 未核销 / 1 已核销。 */
    private Integer verifyStatus;

    private LocalDateTime createdAt;
}
