package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单（文档 1.4）。
 *
 * <p>相对文档 DDL 的补充字段：</p>
 * <ul>
 *   <li>request_id —— 文档 2.4 要求「幂等键写到 order 表并加唯一索引」，DDL 未列出；</li>
 *   <li>pay_no / paid_at —— 支付流水与支付时间；</li>
 *   <li>coupon_id —— 优惠券核销记录（退票时按实付金额算手续费）。</li>
 * </ul>
 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long memberId;

    private Long sessionId;

    /** 逗号分隔的座位 id，如 12,13,14。 */
    private String seatIds;

    /** 展示用座位标签，如 5排6座,5排7座。 */
    private String seatLabels;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    /** 10 待支付 20 已支付 30 已出票 40 已观影 50 退票中 60 已退票 70 已改签 90 已取消。 */
    private Integer status;

    /** 锁座超时时间。 */
    private LocalDateTime lockExpireAt;

    /** 乐观锁版本。 */
    private Integer version;

    private LocalDateTime createdAt;

    /** 前端生成的请求号，唯一索引。 */
    private String requestId;

    private String payNo;

    private LocalDateTime paidAt;

    private Long couponId;
}
