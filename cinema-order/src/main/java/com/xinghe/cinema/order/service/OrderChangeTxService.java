package com.xinghe.cinema.order.service;

import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.common.enums.ChangeStatus;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.util.MoneyUtil;
import com.xinghe.cinema.order.entity.ChangeRecord;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.OrderItem;
import com.xinghe.cinema.order.mapper.ChangeRecordMapper;
import com.xinghe.cinema.order.mapper.OrderItemMapper;
import com.xinghe.cinema.order.mapper.OrderMapper;
import com.xinghe.cinema.order.support.SeatSupport;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.ticket.dto.SeatPrice;
import com.xinghe.cinema.ticket.mapper.TicketMapper;
import com.xinghe.cinema.ticket.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 改签的 DB 事务边界（文档 6.2 第 3 步）。
 *
 * <p>拆成独立 bean 是为了让「Redis 锁新座位」与「DB 事务提交」的先后顺序可控：
 * 编排在 {@link OrderChangeService}，事务只负责「订单 + 明细 + 票 + 改签记录」的原子更新。</p>
 */
@Service
public class OrderChangeTxService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ChangeRecordMapper changeRecordMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;

    public OrderChangeTxService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                ChangeRecordMapper changeRecordMapper, TicketMapper ticketMapper,
                                TicketService ticketService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.changeRecordMapper = changeRecordMapper;
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
    }

    /** 等价换座 / 需退款场景：改签立即生效。 */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRecord applyImmediately(Order order, Session newSession, List<Seat> newSeats,
                                         Map<Long, BigDecimal> priceBySeat, BigDecimal newTotal,
                                         BigDecimal diff, List<Long> oldSeatIds) {
        String oldSeatLabels = order.getSeatLabels();
        applyOrder(order, newSession, newSeats, priceBySeat, newTotal, diff);
        ChangeRecord rec = buildRecord(order, newSession, newSeats, diff, oldSeatIds, oldSeatLabels,
                ChangeStatus.SUCCESS.getCode());
        changeRecordMapper.insert(rec);
        return rec;
    }

    /** 需补款场景：只落「待补款」记录，订单不变（超时由延迟消息回滚）。 */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRecord createPending(Order order, Session newSession, List<Seat> newSeats,
                                      BigDecimal diff, LocalDateTime payExpireAt, List<Long> oldSeatIds) {
        ChangeRecord rec = buildRecord(order, newSession, newSeats, diff, oldSeatIds,
                order.getSeatLabels(), ChangeStatus.PENDING_PAY.getCode());
        rec.setPayExpireAt(payExpireAt);
        changeRecordMapper.insert(rec);
        return rec;
    }

    /** 补款成功：按待补款记录正式生效。 */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRecord applyPending(ChangeRecord rec, Order order, Session newSession,
                                     List<Seat> newSeats, Map<Long, BigDecimal> priceBySeat) {
        BigDecimal newTotal = MoneyUtil.scale2(order.getTotalAmount().add(rec.getDiffAmount()));
        applyOrder(order, newSession, newSeats, priceBySeat, newTotal, rec.getDiffAmount());
        int updated = changeRecordMapper.markPendingStatus(withStatus(rec, ChangeStatus.SUCCESS.getCode()));
        if (updated == 0) {
            throw new BizException("改签记录状态已变更，无法确认");
        }
        rec.setStatus(ChangeStatus.SUCCESS.getCode());
        rec.setNewSeatIds(SeatSupport.joinIds(ids(newSeats)));
        rec.setNewSeatLabels(SeatSupport.joinLabels(newSeats));
        return rec;
    }

    /** 补款超时回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(ChangeRecord rec) {
        changeRecordMapper.markPendingStatus(withStatus(rec, ChangeStatus.ROLLBACK.getCode()));
        rec.setStatus(ChangeStatus.ROLLBACK.getCode());
    }

    /**
     * 订单、明细、票据三处一起切换。
     * 顺序：CAS 改订单（失败即抛，事务回滚）→ 重写明细 → 换票。
     */
    private void applyOrder(Order order, Session newSession, List<Seat> newSeats,
                            Map<Long, BigDecimal> priceBySeat, BigDecimal newTotal, BigDecimal diff) {
        List<Long> newSeatIds = ids(newSeats);
        BigDecimal newPayAmount = MoneyUtil.scale2(order.getPayAmount().add(diff).max(BigDecimal.ZERO));

        int rows = orderMapper.markChanged(order.getId(), newSession.getId(),
                SeatSupport.joinIds(newSeatIds), SeatSupport.joinLabels(newSeats), newTotal, newPayAmount);
        if (rows == 0) {
            throw new BizException("订单状态已变更，改签失败");
        }

        orderItemMapper.deleteByOrder(order.getId());
        List<SeatPrice> seatPrices = new ArrayList<>(newSeats.size());
        for (Seat seat : newSeats) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setSessionId(newSession.getId());
            item.setSeatId(seat.getId());
            item.setSeatLabel(seat.label());
            item.setPrice(priceBySeat.getOrDefault(seat.getId(), BigDecimal.ZERO));
            orderItemMapper.insert(item);
            seatPrices.add(new SeatPrice(seat.getId(), item.getPrice()));
        }

        // 已出票的订单要「换票」：删旧票 → 出票到新座位（uk_session_seat 仍然生效）
        ticketMapper.deleteByOrder(order.getId());
        ticketService.issue(order.getId(), newSession.getId(), seatPrices);

        order.setSessionId(newSession.getId());
        order.setSeatIds(SeatSupport.joinIds(newSeatIds));
        order.setSeatLabels(SeatSupport.joinLabels(newSeats));
        order.setTotalAmount(newTotal);
        order.setPayAmount(newPayAmount);
    }

    private ChangeRecord buildRecord(Order order, Session newSession, List<Seat> newSeats,
                                     BigDecimal diff, List<Long> oldSeatIds, String oldSeatLabels, int status) {
        ChangeRecord rec = new ChangeRecord();
        rec.setOrderId(order.getId());
        rec.setOrderNo(order.getOrderNo());
        rec.setOldSessionId(order.getSessionId());
        rec.setOldSeatIds(SeatSupport.joinIds(oldSeatIds));
        rec.setOldSeatLabels(oldSeatLabels);
        rec.setNewSessionId(newSession.getId());
        rec.setNewSeatIds(SeatSupport.joinIds(ids(newSeats)));
        rec.setNewSeatLabels(SeatSupport.joinLabels(newSeats));
        rec.setDiffAmount(diff);
        rec.setStatus(status);
        rec.setOldReleased(0);
        rec.setCreatedAt(LocalDateTime.now());
        return rec;
    }

    private ChangeRecord withStatus(ChangeRecord rec, int status) {
        ChangeRecord c = new ChangeRecord();
        c.setId(rec.getId());
        c.setStatus(status);
        return c;
    }

    private List<Long> ids(List<Seat> seats) {
        List<Long> ids = new ArrayList<>(seats.size());
        for (Seat s : seats) {
            ids.add(s.getId());
        }
        return ids;
    }
}
