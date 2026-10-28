package com.xinghe.cinema.order.service;

import com.xinghe.cinema.common.constant.CinemaConstants;
import com.xinghe.cinema.common.enums.OrderStatus;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.OrderItem;
import com.xinghe.cinema.order.entity.RefundRecord;
import com.xinghe.cinema.order.mapper.OrderItemMapper;
import com.xinghe.cinema.order.mapper.OrderMapper;
import com.xinghe.cinema.order.mapper.RefundRecordMapper;
import com.xinghe.cinema.payment.callback.PaymentCallbackHandler;
import com.xinghe.cinema.payment.service.RefundService;
import com.xinghe.cinema.ticket.dto.SeatPrice;
import com.xinghe.cinema.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付成功后的订单处理（文档 4.3，本项目资金安全的核心）。
 *
 * <p>时序问题：支付回调与超时释放同时 CAS 同一行，只有一个能成功。</p>
 * <ul>
 *   <li>支付先赢 → 释放的 CAS 影响 0 行，跳过，座位保留；</li>
 *   <li>释放先赢 → 支付回调的 CAS 影响 0 行，此时**钱已收、座位已没**，必须立即退款。</li>
 * </ul>
 */
@Slf4j
@Service
public class OrderPaymentCallbackService implements PaymentCallbackHandler {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final TicketService ticketService;
    private final RefundService paymentRefundService;
    private final RefundRecordMapper refundRecordMapper;

    public OrderPaymentCallbackService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                       TicketService ticketService, RefundService paymentRefundService,
                                       RefundRecordMapper refundRecordMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.ticketService = ticketService;
        this.paymentRefundService = paymentRefundService;
        this.refundRecordMapper = refundRecordMapper;
    }

    @Override
    public CallbackResult onPaySuccess(String orderNo, String payNo, BigDecimal amount) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return CallbackResult.fail("订单不存在: " + orderNo);
        }
        if (order.getPayAmount().compareTo(amount) != 0) {
            log.warn("订单金额与支付金额不一致 orderNo={} order={} pay={}", orderNo, order.getPayAmount(), amount);
            return CallbackResult.fail("支付金额与订单金额不一致");
        }
        if (order.getStatus() == OrderStatus.PAID.getCode() || order.getStatus() == OrderStatus.ISSUED.getCode()) {
            return CallbackResult.ok("订单已支付，重复回调已忽略");
        }

        int rows = orderMapper.markPaid(order.getId(), payNo);
        if (rows == 0) {
            Order fresh = orderMapper.selectById(order.getId());
            return handleCasFailure(fresh, orderNo);
        }

        try {
            issueTickets(order);
            orderMapper.markIssued(order.getId());
            log.info("支付成功并出票 orderNo={} payNo={}", orderNo, payNo);
            return CallbackResult.ok("支付成功，已出票");
        } catch (Exception e) {
            // 出票失败（uk_session_seat 兜底触发）：钱已收，必须退款
            log.error("出票失败，发起全额退款 orderNo={}", orderNo, e);
            refundAndClose(order, order.getPayAmount(), BigDecimal.ZERO, "出票失败，款项原路退回");
            return CallbackResult.fail("出票失败，款项将原路退回");
        }
    }

    /** CAS 失败分支：区分「已支付」与「已被取消」。 */
    private CallbackResult handleCasFailure(Order fresh, String orderNo) {
        if (fresh == null) {
            return CallbackResult.fail("订单不存在: " + orderNo);
        }
        if (fresh.getStatus() == OrderStatus.CANCELED.getCode()) {
            log.warn("支付与超时释放撞车，释放先赢，立即退款 orderNo={}", orderNo);
            refundAndClose(fresh, fresh.getPayAmount(), BigDecimal.ZERO, CinemaConstants.MSG_ORDER_CANCELED_REFUND);
            return CallbackResult.fail(CinemaConstants.MSG_ORDER_CANCELED_REFUND);
        }
        if (fresh.getStatus() == OrderStatus.PAID.getCode() || fresh.getStatus() == OrderStatus.ISSUED.getCode()) {
            return CallbackResult.ok("订单已支付，重复回调已忽略");
        }
        log.warn("订单状态不可支付 orderNo={} status={}", orderNo, fresh.getStatus());
        return CallbackResult.fail("订单当前状态不可支付：" + OrderStatus.of(fresh.getStatus()).getDesc());
    }

    private void issueTickets(Order order) {
        List<OrderItem> items = orderItemMapper.listByOrder(order.getId());
        List<SeatPrice> seatPrices = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            seatPrices.add(new SeatPrice(item.getSeatId(), item.getPrice()));
        }
        ticketService.issue(order.getId(), order.getSessionId(), seatPrices);
    }

    /** 全额/部分退款 + 记录退款单。订单已取消时不再改状态。 */
    private void refundAndClose(Order order, BigDecimal amount, BigDecimal fee, String reason) {
        paymentRefundService.refund(order.getId(), order.getOrderNo(), amount, fee, reason);

        RefundRecord rr = new RefundRecord();
        rr.setOrderId(order.getId());
        rr.setOrderNo(order.getOrderNo());
        rr.setApplyAmount(order.getPayAmount());
        rr.setFeeRate(BigDecimal.ZERO);
        rr.setFee(fee);
        rr.setRefundAmount(amount);
        rr.setReason(reason);
        rr.setStatus(1);
        rr.setCreatedAt(LocalDateTime.now());
        refundRecordMapper.insert(rr);

        // 已支付 → 退票中；退票中 → 已退票（含出票失败场景）
        int s = order.getStatus() == null ? 0 : order.getStatus();
        if (s == OrderStatus.PAID.getCode() || s == OrderStatus.ISSUED.getCode()) {
            if (orderMapper.markRefunding(order.getId()) == 1) {
                orderMapper.markRefunded(order.getId());
            }
        }
    }
}
