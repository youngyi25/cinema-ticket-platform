package com.xinghe.cinema.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinghe.cinema.common.enums.OrderStatus;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.util.MoneyUtil;
import com.xinghe.cinema.member.service.MemberService;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.RefundPolicy;
import com.xinghe.cinema.order.entity.RefundRecord;
import com.xinghe.cinema.order.mapper.OrderMapper;
import com.xinghe.cinema.order.mapper.RefundPolicyMapper;
import com.xinghe.cinema.order.mapper.RefundRecordMapper;
import com.xinghe.cinema.payment.service.RefundService;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.SessionService;
import com.xinghe.cinema.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 退票（文档 6.1）。
 *
 * <p>手续费配置化、按**实付金额**算（不是票价，因为有优惠券）。
 * 退票金额 = 实付 × (1 - 费率)，向下取整到分。</p>
 */
@Slf4j
@Service
public class OrderRefundService {

    private final OrderMapper orderMapper;
    private final RefundPolicyMapper refundPolicyMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final RefundService paymentRefundService;
    private final SessionService sessionService;
    private final TicketService ticketService;
    private final MemberService memberService;
    private final OrderStateMachine stateMachine;

    public OrderRefundService(OrderMapper orderMapper, RefundPolicyMapper refundPolicyMapper,
                              RefundRecordMapper refundRecordMapper, RefundService paymentRefundService,
                              SessionService sessionService, TicketService ticketService,
                              MemberService memberService, OrderStateMachine stateMachine) {
        this.orderMapper = orderMapper;
        this.refundPolicyMapper = refundPolicyMapper;
        this.refundRecordMapper = refundRecordMapper;
        this.paymentRefundService = paymentRefundService;
        this.sessionService = sessionService;
        this.ticketService = ticketService;
        this.memberService = memberService;
        this.stateMachine = stateMachine;
    }

    /**
     * 申请退票：20/30 → 50 → 60，并释放座位、退回优惠券。
     */
    public RefundRecord apply(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在: " + orderId);
        }
        stateMachine.assertCanTransit(order.getStatus(), OrderStatus.REFUNDING.getCode());

        Session session = sessionService.getRequired(order.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(session.getStartTime())) {
            throw new BizException("已开场，不可退票");
        }
        long hours = Duration.between(now, session.getStartTime()).toHours();
        RefundPolicy policy = resolvePolicy(session.getCinemaId(), hours);
        if (policy == null) {
            throw new BizException("按退票规则当前不可退票");
        }

        BigDecimal feeRate = policy.getFeeRate() == null ? BigDecimal.ZERO : policy.getFeeRate();
        BigDecimal payAmount = order.getPayAmount();
        BigDecimal refundAmount = MoneyUtil.floor2(payAmount.multiply(BigDecimal.ONE.subtract(feeRate)));
        BigDecimal fee = MoneyUtil.scale2(payAmount.subtract(refundAmount));

        if (orderMapper.markRefunding(orderId) == 0) {
            throw new BizException("订单状态已变更，退票失败");
        }

        RefundRecord record = new RefundRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setApplyAmount(payAmount);
        record.setFeeRate(feeRate);
        record.setFee(fee);
        record.setRefundAmount(refundAmount);
        record.setReason(reason == null ? "用户申请退票" : reason);
        record.setStatus(0);
        record.setCreatedAt(now);
        refundRecordMapper.insert(record);

        // mock 通道同步退款成功；真实渠道应等异步退款通知再置 60
        paymentRefundService.refund(order.getId(), order.getOrderNo(), refundAmount, fee, record.getReason());
        record.setStatus(1);
        refundRecordMapper.updateStatus(record);
        orderMapper.markRefunded(orderId);

        // 释放座位（删票 + 清位），座位重新可售
        ticketService.revoke(order.getId(), session.getId(), session.getHallId());
        memberService.releaseCoupon(order.getCouponId(), order.getId());

        log.info("退票完成 orderNo={} feeRate={} fee={} refund={}", order.getOrderNo(), feeRate, fee, refundAmount);
        return record;
    }

    public List<RefundRecord> listByOrderNo(String orderNo) {
        return refundRecordMapper.listByOrderNo(orderNo);
    }

    /**
     * 阶梯匹配：优先影院专属规则，没有则用全局规则；取 hours_before ≤ 距开场小时数 的最大档。
     */
    public RefundPolicy resolvePolicy(Long cinemaId, long hoursBefore) {
        List<RefundPolicy> all = refundPolicyMapper.selectList(
                Wrappers.<RefundPolicy>lambdaQuery().eq(RefundPolicy::getEnabled, 1));
        List<RefundPolicy> cinemaRules = all.stream()
                .filter(p -> p.getCinemaId() != null && p.getCinemaId().equals(cinemaId))
                .toList();
        List<RefundPolicy> pool = cinemaRules.isEmpty()
                ? all.stream().filter(p -> p.getCinemaId() == null).toList()
                : cinemaRules;
        return pool.stream()
                .filter(p -> hoursBefore >= (p.getHoursBefore() == null ? 0 : p.getHoursBefore()))
                .max(Comparator.comparingInt((RefundPolicy p) ->
                        p.getHoursBefore() == null ? 0 : p.getHoursBefore()))
                .orElse(null);
    }
}
