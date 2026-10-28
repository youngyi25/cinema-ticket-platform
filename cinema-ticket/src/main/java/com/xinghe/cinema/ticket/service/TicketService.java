package com.xinghe.cinema.ticket.service;

import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.cinema.service.SeatQueryService;
import com.xinghe.cinema.common.enums.SeatType;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.redis.SeatBitmapRedis;
import com.xinghe.cinema.common.util.SeatBitmapUtil;
import com.xinghe.cinema.ticket.dto.SeatPrice;
import com.xinghe.cinema.ticket.entity.Ticket;
import com.xinghe.cinema.ticket.mapper.TicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 票务核心：座位占用 bitmap 的唯一写入方（文档 0.1「其他服务不得直接操作 bitmap」）。
 *
 * <p>这里暴露 lock / release / issue / revoke 四个语义动作，order 服务只调用它们，
 * 不直接碰 Redis 位。</p>
 */
@Slf4j
@Service
public class TicketService {

    private final SeatBitmapRedis bitmapRedis;
    private final TicketMapper ticketMapper;
    private final HallService hallService;
    private final SeatQueryService seatQueryService;

    public TicketService(SeatBitmapRedis bitmapRedis, TicketMapper ticketMapper,
                         HallService hallService, SeatQueryService seatQueryService) {
        this.bitmapRedis = bitmapRedis;
        this.ticketMapper = ticketMapper;
        this.hallService = hallService;
        this.seatQueryService = seatQueryService;
    }

    /**
     * 原子锁座（文档 2.2）。全部成功或全部不改。
     *
     * @throws BizException 座位不存在 / 属于其他影厅 / 损坏 / 已被占
     */
    public void lockSeats(Long sessionId, Long hallId, List<Long> seatIds) {
        Hall hall = hallService.getRequired(hallId);
        List<Seat> seats = seatQueryService.listByIds(seatIds);
        List<Integer> offsets = resolveOffsets(hall, seats, seatIds);

        if (!bitmapRedis.lockSeats(sessionId, offsets)) {
            throw new BizException("座位已被他人锁定，请重新选座");
        }
    }

    /**
     * 批量清位（文档 4.4）。已售座位不释放 —— 防止迟到的释放请求把已出票座位清掉。
     */
    public void releaseSeats(Long sessionId, Long hallId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        Hall hall = hallService.getRequired(hallId);
        List<Seat> seats = seatQueryService.listByIds(seatIds);
        Map<Long, Integer> offsetMap = offsetMap(hall, seats);

        List<Integer> offsets = new ArrayList<>();
        for (Long seatId : seatIds) {
            Integer off = offsetMap.get(seatId);
            if (off == null) {
                continue;
            }
            if (ticketMapper.countSold(sessionId, seatId) > 0) {
                log.warn("跳过释放已售座位 sessionId={} seatId={}", sessionId, seatId);
                continue;
            }
            offsets.add(off);
        }
        long n = bitmapRedis.releaseSeats(sessionId, offsets);
        log.info("释放座位 sessionId={} seats={} cleared={}", sessionId, seatIds, n);
    }

    /**
     * 出票：写 ticket 明细。uk_session_seat 是最后一道防线（文档 1.4），
     * 一旦触发唯一索引冲突说明 bitmap 出了问题，必须把异常抛给调用方回滚订单。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Ticket> issue(Long orderId, Long sessionId, List<SeatPrice> seatPrices) {
        List<Ticket> tickets = new ArrayList<>();
        for (SeatPrice sp : seatPrices) {
            Ticket t = new Ticket();
            t.setOrderId(orderId);
            t.setSessionId(sessionId);
            t.setSeatId(sp.getSeatId());
            t.setPrice(sp.getPrice());
            t.setVerifyCode(genVerifyCode());
            t.setVerifyStatus(0);
            t.setCreatedAt(LocalDateTime.now());
            try {
                ticketMapper.insert(t);
            } catch (DuplicateKeyException e) {
                throw new BizException("出票失败：座位已被占用（sessionId=" + sessionId
                        + ", seatId=" + sp.getSeatId() + "），订单将退款");
            }
            tickets.add(t);
        }
        return tickets;
    }

    /** 退票：删票 + 释放座位，使座位可再次销售。 */
    public void revoke(Long orderId, Long sessionId, Long hallId) {
        List<Ticket> tickets = ticketMapper.listByOrder(orderId);
        List<Long> seatIds = tickets.stream().map(Ticket::getSeatId).collect(Collectors.toList());
        ticketMapper.deleteByOrder(orderId);
        releaseSeats(sessionId, hallId, seatIds);
    }

    public List<Ticket> listByOrder(Long orderId) {
        return ticketMapper.listByOrder(orderId);
    }

    /** 校验座位合法性并换算 offset。越界 / 串厅 / 损坏一律拒绝（文档 2.5）。 */
    public List<Integer> resolveOffsets(Hall hall, List<Seat> seats, List<Long> seatIds) {
        if (seats.size() != seatIds.size()) {
            throw new BizException("存在无效座位");
        }
        Map<Long, Integer> map = offsetMap(hall, seats);
        int capacity = hall.seatCount();
        List<Integer> offsets = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            if (!hall.getId().equals(seat.getHallId())) {
                throw new BizException("座位不属于该影厅: " + seat.getId());
            }
            if (SeatType.BROKEN.getCode() == (seat.getSeatType() == null ? 1 : seat.getSeatType())) {
                throw new BizException("座位已损坏，不可售: " + seat.label());
            }
            Integer off = map.get(seat.getId());
            if (off == null || off < 0 || off >= capacity) {
                throw new BizException("座位偏移量越界: " + seat.getId());
            }
            offsets.add(off);
        }
        return offsets;
    }

    /** 座位 id → bitmap offset。offset 必须紧凑连续，不能用 seat.id（文档 1.2）。 */
    public Map<Long, Integer> offsetMap(Hall hall, List<Seat> seats) {
        Map<Long, Integer> map = new HashMap<>(seats.size() * 2);
        for (Seat s : seats) {
            map.put(s.getId(), SeatBitmapUtil.offset(s.getRowNo(), s.getColNo(), hall.getColCount()));
        }
        return map;
    }

    private String genVerifyCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
