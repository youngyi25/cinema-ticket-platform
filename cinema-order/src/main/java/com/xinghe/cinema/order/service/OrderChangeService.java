package com.xinghe.cinema.order.service;

import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.cinema.service.SeatQueryService;
import com.xinghe.cinema.common.constant.CinemaConstants;
import com.xinghe.cinema.common.delay.DelayMessagePublisher;
import com.xinghe.cinema.common.enums.ChangeStatus;
import com.xinghe.cinema.common.enums.OrderStatus;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.util.MoneyUtil;
import com.xinghe.cinema.order.dto.ChangeRequest;
import com.xinghe.cinema.order.dto.ChangeResult;
import com.xinghe.cinema.order.entity.ChangeRecord;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.mapper.ChangeRecordMapper;
import com.xinghe.cinema.order.mapper.OrderMapper;
import com.xinghe.cinema.order.props.OrderProperties;
import com.xinghe.cinema.order.support.SeatSupport;
import com.xinghe.cinema.payment.service.RefundService;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.PricingService;
import com.xinghe.cinema.schedule.service.SessionService;
import com.xinghe.cinema.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 改签（文档 6.2）：先占后放。
 *
 * <pre>
 * 1. 校验新场次可售、新座位可用（预检，不锁）
 * 2. Lua 锁新座位          —— 失败直接返回，什么都没变
 * 3. DB 事务：改 order_item / order → 70 / 写 change_record
 * 4. 事务提交成功 → Lua 释放旧座位（失败只记日志，由补偿任务补清）
 * 5. 事务提交失败 → Lua 释放新座位（补偿）
 * </pre>
 *
 * <p>宁可泄漏座位，不可让用户丢票。</p>
 */
@Slf4j
@Service
public class OrderChangeService {

    private final OrderMapper orderMapper;
    private final ChangeRecordMapper changeRecordMapper;
    private final SessionService sessionService;
    private final HallService hallService;
    private final SeatQueryService seatQueryService;
    private final PricingService pricingService;
    private final TicketService ticketService;
    private final OrderChangeTxService txService;
    private final DelayMessagePublisher delayPublisher;
    private final RefundService paymentRefundService;
    private final OrderProperties properties;

    public OrderChangeService(OrderMapper orderMapper, ChangeRecordMapper changeRecordMapper,
                              SessionService sessionService, HallService hallService,
                              SeatQueryService seatQueryService, PricingService pricingService,
                              TicketService ticketService, OrderChangeTxService txService,
                              DelayMessagePublisher delayPublisher, RefundService paymentRefundService,
                              OrderProperties properties) {
        this.orderMapper = orderMapper;
        this.changeRecordMapper = changeRecordMapper;
        this.sessionService = sessionService;
        this.hallService = hallService;
        this.seatQueryService = seatQueryService;
        this.pricingService = pricingService;
        this.ticketService = ticketService;
        this.txService = txService;
        this.delayPublisher = delayPublisher;
        this.paymentRefundService = paymentRefundService;
        this.properties = properties;
    }

    public ChangeResult change(ChangeRequest req) {
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException("订单不存在: " + req.getOrderId());
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.ISSUED.getCode())) {
            throw new BizException("仅已出票订单可改签，当前状态："
                    + OrderStatus.of(order.getStatus()).getDesc());
        }
        if (Objects.equals(order.getSessionId(), req.getNewSessionId())) {
            throw new BizException("改签场次不能与原场次相同");
        }

        List<Long> oldSeatIds = SeatSupport.parseIds(order.getSeatIds());
        Session oldSession = sessionService.getRequired(order.getSessionId());
        Session newSession = sessionService.getSellable(req.getNewSessionId());
        Hall newHall = hallService.getRequired(newSession.getHallId());

        List<Long> newSeatIds = new ArrayList<>(new LinkedHashSet<>(req.getNewSeatIds()));
        if (newSeatIds.size() != req.getNewSeatIds().size()) {
            throw new BizException("存在重复座位");
        }
        if (newSeatIds.size() != oldSeatIds.size()) {
            throw new BizException("改签座位数需与原订单一致（原 " + oldSeatIds.size() + " 张）");
        }

        List<Seat> newSeats = seatQueryService.listByIds(newSeatIds);
        Map<Long, BigDecimal> priceBySeat = priceBySeat(newSession, newHall, newSeats);
        BigDecimal newTotal = MoneyUtil.scale2(newSeats.stream()
                .map(s -> priceBySeat.get(s.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal diff = MoneyUtil.scale2(newTotal.subtract(order.getTotalAmount()));

        // 先占
        ticketService.lockSeats(newSession.getId(), newHall.getId(), newSeatIds);

        try {
            if (diff.signum() > 0) {
                return createPendingPay(order, newSession, newSeats, diff, oldSeatIds);
            }
            ChangeRecord rec = txService.applyImmediately(order, newSession, newSeats, priceBySeat,
                    newTotal, diff, oldSeatIds);
            releaseOldSeats(oldSession, oldSeatIds, rec);
            if (diff.signum() < 0) {
                // 需退款：改签立即生效，退款异步处理
                paymentRefundService.refund(order.getId(), order.getOrderNo(), diff.negate(),
                        BigDecimal.ZERO, "改签差价退款");
            }
            return result(order.getOrderNo(), rec, diff, false, "改签成功");
        } catch (RuntimeException e) {
            // 补偿：释放新座位
            try {
                ticketService.releaseSeats(newSession.getId(), newHall.getId(), newSeatIds);
            } catch (Exception ex) {
                log.error("改签失败补偿释放新座位失败 sessionId={} seats={}", newSession.getId(), newSeatIds, ex);
            }
            throw e;
        }
    }

    private ChangeResult createPendingPay(Order order, Session newSession, List<Seat> newSeats,
                                          BigDecimal diff, List<Long> oldSeatIds) {
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(properties.getChangePayTimeoutMinutes());
        ChangeRecord rec = txService.createPending(order, newSession, newSeats, diff, expireAt, oldSeatIds);
        delayPublisher.publish(CinemaConstants.TOPIC_CHANGE_TIMEOUT, String.valueOf(rec.getId()),
                Duration.ofMinutes(properties.getChangePayTimeoutMinutes()));
        return result(order.getOrderNo(), rec, diff, true,
                "改签需补差价 " + diff + " 元，请在 " + properties.getChangePayTimeoutMinutes() + " 分钟内支付");
    }

    /** 补款成功（mock：直接确认）。 */
    public ChangeResult confirmPay(Long changeId) {
        ChangeRecord rec = changeRecordMapper.findById(changeId);
        if (rec == null) {
            throw new BizException("改签记录不存在");
        }
        if (!Objects.equals(rec.getStatus(), ChangeStatus.PENDING_PAY.getCode())) {
            throw new BizException("改签记录状态不是待补款");
        }
        if (rec.getPayExpireAt() != null && rec.getPayExpireAt().isBefore(LocalDateTime.now())) {
            rollbackPending(changeId);
            throw new BizException("补款已超时，改签已回滚");
        }
        Order order = orderMapper.selectById(rec.getOrderId());
        Session newSession = sessionService.getRequired(rec.getNewSessionId());
        Hall newHall = hallService.getRequired(newSession.getHallId());
        List<Long> newSeatIds = SeatSupport.parseIds(rec.getNewSeatIds());
        List<Seat> newSeats = seatQueryService.listByIds(newSeatIds);
        Map<Long, BigDecimal> priceBySeat = priceBySeat(newSession, newHall, newSeats);

        txService.applyPending(rec, order, newSession, newSeats, priceBySeat);
        Session oldSession = sessionService.getRequired(rec.getOldSessionId());
        releaseOldSeats(oldSession, SeatSupport.parseIds(rec.getOldSeatIds()), rec);
        log.info("补款完成，改签生效 orderNo={} changeId={}", order.getOrderNo(), changeId);
        return result(order.getOrderNo(), rec, rec.getDiffAmount(), false, "改签成功");
    }

    /** 补款超时回滚：释放新座位，订单保持原样。 */
    public void rollbackPending(Long changeId) {
        ChangeRecord rec = changeRecordMapper.findById(changeId);
        if (rec == null || !Objects.equals(rec.getStatus(), ChangeStatus.PENDING_PAY.getCode())) {
            return;
        }
        try {
            Session newSession = sessionService.getRequired(rec.getNewSessionId());
            ticketService.releaseSeats(newSession.getId(), newSession.getHallId(),
                    SeatSupport.parseIds(rec.getNewSeatIds()));
            txService.rollback(rec);
            log.info("改签补款超时，已回滚 changeId={}", changeId);
        } catch (Exception e) {
            log.error("改签回滚失败 changeId={}", changeId, e);
        }
    }

    /**
     * 释放旧座位。失败**不能影响改签结果** —— 只记日志，由补偿任务（change_record.old_released=0）补清。
     */
    private void releaseOldSeats(Session oldSession, List<Long> oldSeatIds, ChangeRecord rec) {
        try {
            ticketService.releaseSeats(oldSession.getId(), oldSession.getHallId(), oldSeatIds);
            changeRecordMapper.markOldReleased(rec.getId());
        } catch (Exception e) {
            log.error("释放旧座位失败 orderNo={} seats={}，等待补偿", rec.getOrderNo(), oldSeatIds, e);
        }
    }

    /** 座位泄漏补偿：扫描 old_released=0 的改签记录。 */
    public int repairLeakedOldSeats() {
        List<ChangeRecord> list = changeRecordMapper.findUnreleased();
        int fixed = 0;
        for (ChangeRecord rec : list) {
            try {
                Session oldSession = sessionService.getRequired(rec.getOldSessionId());
                ticketService.releaseSeats(oldSession.getId(), oldSession.getHallId(),
                        SeatSupport.parseIds(rec.getOldSeatIds()));
                changeRecordMapper.markOldReleased(rec.getId());
                fixed++;
            } catch (Exception e) {
                log.error("补偿释放旧座位失败 changeId={}", rec.getId(), e);
            }
        }
        return fixed;
    }

    public List<ChangeRecord> listByOrder(Long orderId) {
        return changeRecordMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<ChangeRecord>lambdaQuery().eq(ChangeRecord::getOrderId, orderId).orderByDesc(ChangeRecord::getId));
    }

    private Map<Long, BigDecimal> priceBySeat(Session session, Hall hall, List<Seat> seats) {
        Map<String, BigDecimal> areaPrice = pricingService.previewByArea(session, hall.getHallType(),
                List.of("A", "B", "C"));
        Map<Long, BigDecimal> map = new HashMap<>(seats.size() * 2);
        for (Seat s : seats) {
            map.put(s.getId(), areaPrice.getOrDefault(s.getAreaCode(), session.getBasePrice()));
        }
        return map;
    }

    private ChangeResult result(String orderNo, ChangeRecord rec, BigDecimal diff, boolean needPay, String msg) {
        ChangeResult r = new ChangeResult();
        r.setOrderNo(orderNo);
        r.setChangeId(rec.getId());
        r.setStatus(rec.getStatus());
        r.setDiffAmount(diff);
        r.setNeedPay(needPay);
        r.setPayExpireAt(rec.getPayExpireAt());
        r.setMessage(msg);
        return r;
    }
}
