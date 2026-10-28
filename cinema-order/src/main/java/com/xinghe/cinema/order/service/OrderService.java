package com.xinghe.cinema.order.service;

import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.service.CinemaService;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.cinema.service.SeatQueryService;
import com.xinghe.cinema.common.constant.CinemaConstants;
import com.xinghe.cinema.common.delay.DelayMessagePublisher;
import com.xinghe.cinema.common.enums.OrderStatus;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.redis.RedisKeys;
import com.xinghe.cinema.common.util.MoneyUtil;
import com.xinghe.cinema.member.service.MemberService;
import com.xinghe.cinema.movie.entity.Movie;
import com.xinghe.cinema.movie.service.MovieService;
import com.xinghe.cinema.order.dto.LockOrderRequest;
import com.xinghe.cinema.order.dto.OrderVO;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.OrderItem;
import com.xinghe.cinema.order.mapper.OrderItemMapper;
import com.xinghe.cinema.order.mapper.OrderMapper;
import com.xinghe.cinema.order.props.OrderProperties;
import com.xinghe.cinema.payment.service.PaymentService;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.PricingService;
import com.xinghe.cinema.schedule.service.SessionService;
import com.xinghe.cinema.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单主流程：锁座下单、取消、超时释放（文档 0.2 / 2 / 4）。
 *
 * <p>这套实现里最值钱的一段在支付回调处（见 {@link OrderPaymentCallbackService}）：
 * 支付与超时释放撞车时必须退款，不实现就有资金风险。</p>
 */
@Slf4j
@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SessionService sessionService;
    private final HallService hallService;
    private final CinemaService cinemaService;
    private final MovieService movieService;
    private final SeatQueryService seatQueryService;
    private final TicketService ticketService;
    private final PricingService pricingService;
    private final MemberService memberService;
    private final PaymentService paymentService;
    private final DelayMessagePublisher delayPublisher;
    private final OrderProperties properties;
    private final OrderStateMachine stateMachine;
    private final StringRedisTemplate redis;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper, SessionService sessionService,
                        HallService hallService, CinemaService cinemaService, MovieService movieService,
                        SeatQueryService seatQueryService, TicketService ticketService, PricingService pricingService,
                        MemberService memberService, PaymentService paymentService,
                        DelayMessagePublisher delayPublisher, OrderProperties properties,
                        OrderStateMachine stateMachine, StringRedisTemplate redis) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.sessionService = sessionService;
        this.hallService = hallService;
        this.cinemaService = cinemaService;
        this.movieService = movieService;
        this.seatQueryService = seatQueryService;
        this.ticketService = ticketService;
        this.pricingService = pricingService;
        this.memberService = memberService;
        this.paymentService = paymentService;
        this.delayPublisher = delayPublisher;
        this.properties = properties;
        this.stateMachine = stateMachine;
        this.redis = redis;
    }

    /**
     * 提交订单：幂等键 → 校验 → Lua 原子锁座 → 落单 → 发延迟消息（文档 0.2）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO lock(LockOrderRequest req) {
        String idemKey = RedisKeys.idemOrder(req.getMemberId(), req.getRequestId());
        Boolean first = redis.opsForValue().setIfAbsent(idemKey, "1",
                Duration.ofMinutes(CinemaConstants.IDEM_TTL_MINUTES));
        if (!Boolean.TRUE.equals(first)) {
            Order exist = orderMapper.findByRequestId(req.getMemberId(), req.getRequestId());
            if (exist != null) {
                log.info("幂等命中，返回原订单 {}", exist.getOrderNo());
                return detail(exist);
            }
            throw new BizException("请求正在处理中，请稍后重试（请勿重复提交）");
        }

        try {
            return doLock(req);
        } catch (RuntimeException e) {
            // 失败后释放幂等键，允许用户用同一 requestId 重试（文档 2.5「重试要换新的 requestId」的放宽版）
            redis.delete(idemKey);
            throw e;
        }
    }

    private OrderVO doLock(LockOrderRequest req) {
        memberService.getRequired(req.getMemberId());
        Session session = sessionService.getSellable(req.getSessionId());
        Hall hall = hallService.getRequired(session.getHallId());

        List<Long> seatIds = new ArrayList<>(new LinkedHashSet<>(req.getSeatIds()));
        if (seatIds.size() != req.getSeatIds().size()) {
            throw new BizException("存在重复座位");
        }
        if (seatIds.size() > CinemaConstants.MAX_SEATS_PER_ORDER) {
            throw new BizException("单笔最多购买 " + CinemaConstants.MAX_SEATS_PER_ORDER + " 张");
        }
        if (orderMapper.countUnpaid(req.getMemberId(), req.getSessionId()) > 0) {
            throw new BizException("该场次已有未支付订单，请先支付或取消");
        }

        List<Seat> seats = seatQueryService.listByIds(seatIds);
        Map<Long, Seat> seatMap = seats.stream().collect(Collectors.toMap(Seat::getId, s -> s));

        Map<String, BigDecimal> areaPrice = pricingService.previewByArea(
                session, hall.getHallType(), List.of("A", "B", "C"));
        List<OrderItem> items = new ArrayList<>(seatIds.size());
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder labels = new StringBuilder();
        for (Long seatId : seatIds) {
            Seat seat = seatMap.get(seatId);
            BigDecimal price = areaPrice.getOrDefault(seat.getAreaCode(), session.getBasePrice());
            total = total.add(price);
            OrderItem item = new OrderItem();
            item.setSessionId(session.getId());
            item.setSeatId(seatId);
            item.setSeatLabel(seat.label());
            item.setPrice(price);
            items.add(item);
            if (labels.length() > 0) {
                labels.append(',');
            }
            labels.append(seat.label());
        }
        total = MoneyUtil.scale2(total);

        BigDecimal discount = BigDecimal.ZERO;
        if (req.getCouponId() != null) {
            discount = memberService.discount(req.getMemberId(), req.getCouponId(), total);
        }
        BigDecimal payAmount = MoneyUtil.scale2(total.subtract(discount).max(BigDecimal.ZERO));

        // 原子锁座：失败直接抛出，什么都没写
        ticketService.lockSeats(session.getId(), hall.getId(), seatIds);
        registerReleaseOnRollback(session.getId(), hall.getId(), seatIds);

        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setMemberId(req.getMemberId());
        order.setSessionId(session.getId());
        order.setSeatIds(seatIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        order.setSeatLabels(labels.toString());
        order.setTotalAmount(total);
        order.setPayAmount(payAmount);
        order.setStatus(OrderStatus.WAIT_PAY.getCode());
        order.setLockExpireAt(LocalDateTime.now().plusMinutes(properties.getLockTimeoutMinutes()));
        order.setVersion(0);
        order.setCreatedAt(LocalDateTime.now());
        order.setRequestId(req.getRequestId());
        order.setCouponId(req.getCouponId());
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
        memberService.markCouponUsed(req.getCouponId(), order.getId());
        paymentService.createForOrder(order.getId(), order.getOrderNo(), payAmount);

        // 主通道：延迟消息准时释放（文档 4.1）
        delayPublisher.publish(CinemaConstants.TOPIC_ORDER_TIMEOUT, order.getOrderNo(),
                Duration.ofMinutes(properties.getLockTimeoutMinutes()));

        OrderVO vo = OrderVO.of(order);
        vo.setItems(items);
        return vo;
    }

    /** 事务回滚时释放已锁座位，避免「位锁了、单没落」。 */
    private void registerReleaseOnRollback(Long sessionId, Long hallId, List<Long> seatIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        ticketService.releaseSeats(sessionId, hallId, seatIds);
                        log.warn("订单事务回滚，已释放座位 sessionId={} seats={}", sessionId, seatIds);
                    } catch (Exception e) {
                        log.error("回滚释放座位失败，等待补偿 sessionId={} seats={}", sessionId, seatIds, e);
                    }
                }
            }
        });
    }

    /** 用户主动取消（10 → 90）→ 清位。 */
    public void cancel(Long orderId, Long memberId) {
        Order order = getRequired(orderId);
        if (memberId != null && !order.getMemberId().equals(memberId)) {
            throw new BizException("无权操作该订单");
        }
        stateMachine.assertCanTransit(order.getStatus(), OrderStatus.CANCELED.getCode());
        int rows = orderMapper.cancel(orderId);
        if (rows == 0) {
            throw new BizException("订单状态已变更，取消失败");
        }
        releaseSeatsQuietly(order);
        memberService.releaseCoupon(order.getCouponId(), order.getId());
        log.info("订单已取消 orderNo={}", order.getOrderNo());
    }

    /**
     * 超时释放（文档 4.2）：一条带条件的 UPDATE 解决重复消费、并发释放、误释放已支付订单。
     *
     * @return true 表示本次真的释放了
     */
    public boolean releaseIfExpired(Order order) {
        int rows = orderMapper.cancelIfExpired(order.getId());
        if (rows == 0) {
            // 已被支付或被别的线程释放，直接跳过
            return false;
        }
        releaseSeatsQuietly(order);
        memberService.releaseCoupon(order.getCouponId(), order.getId());
        log.info("订单超时释放 orderNo={} seats={}", order.getOrderNo(), order.getSeatIds());
        return true;
    }

    public boolean releaseIfExpired(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        return order != null && releaseIfExpired(order);
    }

    public void releaseSeatsQuietly(Order order) {
        try {
            Session session = sessionService.getRequired(order.getSessionId());
            ticketService.releaseSeats(session.getId(), session.getHallId(), seatIdsOf(order));
        } catch (Exception e) {
            log.error("释放座位失败 orderNo={}，等待对账补偿", order.getOrderNo(), e);
        }
    }

    public Order getRequired(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("订单不存在: " + orderId);
        }
        return o;
    }

    public Order getByOrderNo(String orderNo) {
        Order o = orderMapper.findByOrderNo(orderNo);
        if (o == null) {
            throw new BizException("订单不存在: " + orderNo);
        }
        return o;
    }

    public OrderVO detail(Order order) {
        OrderVO vo = OrderVO.of(order);
        vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
        vo.setItems(orderItemMapper.listByOrder(order.getId()));
        vo.setTickets(ticketService.listByOrder(order.getId()));
        try {
            Session s = sessionService.getRequired(order.getSessionId());
            vo.setStartTime(s.getStartTime());
            vo.setEndTime(s.getEndTime());
            Movie m = movieService.getRequired(s.getMovieId());
            vo.setMovieName(m.getName());
            Hall h = hallService.getRequired(s.getHallId());
            vo.setHallName(h.getName());
            Cinema c = cinemaService.getRequired(s.getCinemaId());
            vo.setCinemaName(c.getName());
        } catch (Exception e) {
            log.warn("补全订单展示信息失败 orderNo={}: {}", order.getOrderNo(), e.getMessage());
        }
        return vo;
    }

    public OrderVO detail(Long orderId) {
        return detail(getRequired(orderId));
    }

    public OrderVO detailByOrderNo(String orderNo) {
        return detail(getByOrderNo(orderNo));
    }

    public List<Order> listByMember(Long memberId) {
        return orderMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Order>lambdaQuery()
                .eq(Order::getMemberId, memberId).orderByDesc(Order::getId));
    }

    public List<Order> findExpiredBatch(int limit) {
        return orderMapper.findExpired(limit);
    }

    public List<Long> seatIdsOf(Order order) {
        List<Long> ids = new ArrayList<>();
        if (order.getSeatIds() == null || order.getSeatIds().isBlank()) {
            return ids;
        }
        for (String s : order.getSeatIds().split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                ids.add(Long.parseLong(t));
            }
        }
        return ids;
    }

    private String genOrderNo() {
        return "SO" + LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
