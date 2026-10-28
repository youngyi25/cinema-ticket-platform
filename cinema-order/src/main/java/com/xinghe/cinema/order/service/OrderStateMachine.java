package com.xinghe.cinema.order.service;

import com.xinghe.cinema.common.enums.OrderStatus;
import com.xinghe.cinema.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（文档 6.4）。
 *
 * <pre>
 * 10 待支付 ──支付成功──→ 20 已支付 ──出票──→ 30 已出票 ──开场──→ 40 已观影
 *    │                        │                    │
 *    │超时/取消                │申请退票             │申请退票 / 申请改签
 *    ↓                        ↓                    ↓
 * 90 已取消              50 退票中 ──退款成功──→ 60 已退票      70 已改签
 * </pre>
 *
 * <p>状态机只负责合法性校验；真正的并发安全由 Mapper 里「带原状态条件的 UPDATE」保证。</p>
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.WAIT_PAY, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELED));
        ALLOWED.put(OrderStatus.PAID, EnumSet.of(OrderStatus.ISSUED, OrderStatus.REFUNDING));
        ALLOWED.put(OrderStatus.ISSUED, EnumSet.of(OrderStatus.WATCHED, OrderStatus.REFUNDING, OrderStatus.CHANGED));
        ALLOWED.put(OrderStatus.REFUNDING, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED.put(OrderStatus.WATCHED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CHANGED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransit(int from, int to) {
        Set<OrderStatus> targets = ALLOWED.get(OrderStatus.of(from));
        return targets != null && targets.contains(OrderStatus.of(to));
    }

    public void assertCanTransit(int from, int to) {
        if (!canTransit(from, to)) {
            throw new BizException("非法状态流转：" + OrderStatus.of(from).getDesc()
                    + "(" + from + ") → " + OrderStatus.of(to).getDesc() + "(" + to + ")");
        }
    }

    public boolean isPayable(Integer status) {
        return status != null && status == OrderStatus.WAIT_PAY.getCode();
    }

    /** 已支付 / 已出票 都可以退票。 */
    public boolean isRefundable(Integer status) {
        return status != null && (status == OrderStatus.PAID.getCode() || status == OrderStatus.ISSUED.getCode());
    }

    public boolean isPaidOrIssued(Integer status) {
        return status != null && (status == OrderStatus.PAID.getCode() || status == OrderStatus.ISSUED.getCode());
    }
}
