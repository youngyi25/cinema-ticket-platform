package com.xinghe.cinema.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinghe.cinema.common.enums.SessionStatus;
import com.xinghe.cinema.common.redis.SeatBitmapRedis;
import com.xinghe.cinema.common.util.SeatBitmapUtil;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.mapper.SessionMapper;
import com.xinghe.cinema.schedule.service.SessionService;
import com.xinghe.cinema.ticket.dto.SeatOccRow;
import com.xinghe.cinema.ticket.mapper.TicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * bitmap 重建（文档 4.5）：Redis 数据丢失后，以 MySQL 已售明细为准重建。
 *
 * <p>重建期间该场次打标 session.rebuilding=1 并停售，否则会重复售出。</p>
 */
@Slf4j
@Service
public class BitmapRebuildService {

    private final SeatBitmapRedis bitmapRedis;
    private final TicketMapper ticketMapper;
    private final SessionMapper sessionMapper;
    private final SessionService sessionService;

    public BitmapRebuildService(SeatBitmapRedis bitmapRedis, TicketMapper ticketMapper,
                                SessionMapper sessionMapper, SessionService sessionService) {
        this.bitmapRedis = bitmapRedis;
        this.ticketMapper = ticketMapper;
        this.sessionMapper = sessionMapper;
        this.sessionService = sessionService;
    }

    /** 全量重建未开场场次。 */
    public RebuildResult rebuildAll() {
        List<Session> sessions = sessionMapper.selectList(Wrappers.<Session>lambdaQuery()
                .gt(Session::getStartTime, LocalDateTime.now())
                .eq(Session::getStatus, SessionStatus.ON_SALE.getCode())
                .eq(Session::getRebuilding, 0));

        Map<Long, List<Integer>> offsetsBySession = new LinkedHashMap<>();
        for (SeatOccRow row : ticketMapper.selectSoldSeatPositions()) {
            offsetsBySession.computeIfAbsent(row.getSessionId(), k -> new ArrayList<>())
                    .add(SeatBitmapUtil.offset(row.getRowNo(), row.getColNo(), row.getColCount()));
        }
        for (SeatOccRow row : ticketMapper.selectHeldSeatPositions()) {
            offsetsBySession.computeIfAbsent(row.getSessionId(), k -> new ArrayList<>())
                    .add(SeatBitmapUtil.offset(row.getRowNo(), row.getColNo(), row.getColCount()));
        }

        int seatCount = 0;
        for (Session s : sessions) {
            sessionService.markRebuilding(s.getId(), true);
            try {
                bitmapRedis.delete(s.getId());
                List<Integer> offsets = offsetsBySession.getOrDefault(s.getId(), List.of());
                seatCount += (int) bitmapRedis.markOccupied(s.getId(), offsets);
            } catch (Exception e) {
                log.error("重建场次 {} 失败", s.getId(), e);
            } finally {
                sessionService.markRebuilding(s.getId(), false);
            }
        }
        log.info("bitmap 重建完成：场次 {} 个，座位 {} 个", sessions.size(), seatCount);
        return new RebuildResult(sessions.size(), seatCount);
    }

    public record RebuildResult(int sessions, int seats) {
    }
}
