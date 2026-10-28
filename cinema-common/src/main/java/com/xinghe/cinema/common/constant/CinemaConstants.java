package com.xinghe.cinema.common.constant;

/**
 * 业务常量。
 */
public final class CinemaConstants {

    private CinemaConstants() {
    }

    /** 单笔订单最多购票张数（文档 2.2「座位数上限受业务限制」）。 */
    public static final int MAX_SEATS_PER_ORDER = 6;

    /** 锁座超时默认时长（分钟），与文档 4.1 的 15 分钟一致。 */
    public static final int DEFAULT_LOCK_MINUTES = 15;

    /** 下单幂等键 TTL（分钟）。 */
    public static final int IDEM_TTL_MINUTES = 5;

    /** 延迟消息 topic：订单锁座超时释放。 */
    public static final String TOPIC_ORDER_TIMEOUT = "order.timeout.release";

    /** 延迟消息 topic：改签补款超时回滚。 */
    public static final String TOPIC_CHANGE_TIMEOUT = "change.pay.timeout.rollback";

    /** 支付回调业务消息。 */
    public static final String MSG_ORDER_CANCELED_REFUND = "订单已超时取消，支付款项原路退回";
}
