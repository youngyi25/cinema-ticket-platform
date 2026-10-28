package com.xinghe.cinema.schedule.service;

import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.service.CinemaService;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.common.enums.SessionStatus;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.lock.DistributedLock;
import com.xinghe.cinema.common.redis.RedisKeys;
import com.xinghe.cinema.movie.entity.Movie;
import com.xinghe.cinema.movie.service.MovieService;
import com.xinghe.cinema.schedule.dto.BatchScheduleRequest;
import com.xinghe.cinema.schedule.dto.CreateSessionRequest;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.mapper.SessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排片服务：冲突检测（文档第 5 节）。
 *
 * <p>并发排片的竞态靠「按 hall_id 加分布式锁」解决：session 表在 (hall_id, start_time)
 * 上没有唯一约束，只靠 SELECT 判断挡不住并发插入。</p>
 */
@Slf4j
@Service
public class SessionService {

    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 10;

    private final SessionMapper sessionMapper;
    private final CinemaService cinemaService;
    private final HallService hallService;
    private final MovieService movieService;
    private final DistributedLock distributedLock;
    private final PriceRuleService priceRuleService;

    public SessionService(SessionMapper sessionMapper,
                          CinemaService cinemaService,
                          HallService hallService,
                          MovieService movieService,
                          DistributedLock distributedLock,
                          PriceRuleService priceRuleService) {
        this.sessionMapper = sessionMapper;
        this.cinemaService = cinemaService;
        this.hallService = hallService;
        this.movieService = movieService;
        this.distributedLock = distributedLock;
        this.priceRuleService = priceRuleService;
    }

    public Session getRequired(Long id) {
        Session s = sessionMapper.selectById(id);
        if (s == null) {
            throw new BizException("场次不存在: " + id);
        }
        return s;
    }

    /**
     * 可售校验：状态可售、未在 bitmap 重建、未开场。
     * 锁座与下单前必须过这一关（文档 2.5）。
     */
    public Session getSellable(Long id) {
        Session s = getRequired(id);
        if (s.getStatus() == null || s.getStatus() != SessionStatus.ON_SALE.getCode()) {
            throw new BizException("场次已停售");
        }
        if (s.getRebuilding() != null && s.getRebuilding() == 1) {
            throw new BizException("场次座位数据重建中，暂停售票");
        }
        if (!s.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BizException("场次已开场，无法购票");
        }
        return s;
    }

    /** 单场排片。 */
    public Session create(CreateSessionRequest req) {
        Session session = build(req);
        Long hallId = session.getHallId();
        String lockKey = RedisKeys.scheduleHallLock(hallId);
        if (!distributedLock.tryLock(lockKey, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS)) {
            throw new BizException("该影厅正在排片，请稍后重试");
        }
        try {
            List<Session> conflicts = sessionMapper.findConflicts(hallId, session.getStartTime(), session.getEndTime());
            if (!conflicts.isEmpty()) {
                throw new BizException("场次时间冲突：" + describe(conflicts));
            }
            sessionMapper.insert(session);
            return session;
        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    /**
     * 批量排片（文档 5.4）：要么全成功要么全失败。
     * 1. 批内自检（同一影厅两两不重叠）  2. 按 hall 分组各加一次锁  3. 事务内逐场查冲突 + 插入
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Session> createBatch(BatchScheduleRequest req) {
        List<Session> sessions = new ArrayList<>();
        for (CreateSessionRequest r : req.getSessions()) {
            sessions.add(build(r));
        }
        selfCheck(sessions);

        Map<Long, List<Session>> byHall = new LinkedHashMap<>();
        for (Session s : sessions) {
            byHall.computeIfAbsent(s.getHallId(), k -> new ArrayList<>()).add(s);
        }

        List<String> lockedKeys = new ArrayList<>();
        try {
            for (Long hallId : byHall.keySet()) {
                String key = RedisKeys.scheduleHallLock(hallId);
                if (!distributedLock.tryLock(key, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS)) {
                    throw new BizException("影厅 " + hallId + " 正在排片，请稍后重试");
                }
                lockedKeys.add(key);
            }
            for (Map.Entry<Long, List<Session>> e : byHall.entrySet()) {
                List<Session> list = e.getValue();
                list.sort(Comparator.comparing(Session::getStartTime));
                for (Session s : list) {
                    List<Session> conflicts = sessionMapper.findConflicts(e.getKey(), s.getStartTime(), s.getEndTime());
                    if (!conflicts.isEmpty()) {
                        throw new BizException("影厅 " + e.getKey() + " 场次冲突：" + describe(conflicts));
                    }
                    sessionMapper.insert(s);
                }
            }
        } finally {
            for (String key : lockedKeys) {
                distributedLock.unlock(key);
            }
        }
        return sessions;
    }

    public void updateStatus(Long id, int status) {
        Session s = getRequired(id);
        s.setStatus(status);
        sessionMapper.updateById(s);
    }

    public List<Session> listByMovie(Long movieId, LocalDate date) {
        LocalDateTime from = date == null ? LocalDateTime.now() : date.atStartOfDay();
        return sessionMapper.listSellableByMovie(movieId, from);
    }

    public List<Session> listByCinema(Long cinemaId, LocalDate date) {
        LocalDateTime from = date == null ? LocalDate.now().atStartOfDay() : date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        return sessionMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Session>lambdaQuery()
                .eq(Session::getCinemaId, cinemaId)
                .ge(Session::getStartTime, from)
                .lt(Session::getStartTime, to)
                .ne(Session::getStatus, SessionStatus.CANCELED.getCode())
                .orderByAsc(Session::getStartTime));
    }

    /** bitmap 重建期间停售打标（文档 4.5）。 */
    public void markRebuilding(Long sessionId, boolean rebuilding) {
        sessionMapper.markRebuilding(sessionId, rebuilding ? 1 : 0);
    }

    /**
     * 组装场次：end_time = start_time + 片长 + 影院清场时长。
     */
    private Session build(CreateSessionRequest req) {
        Hall hall = hallService.getRequired(req.getHallId());
        Cinema cinema = cinemaService.getRequired(req.getCinemaId());
        if (!hall.getCinemaId().equals(cinema.getId())) {
            throw new BizException("影厅不属于该影院");
        }
        Movie movie = movieService.getOnShelfRequired(req.getMovieId());
        if (req.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException("开场时间不能早于当前时间");
        }

        int cleanup = cinema.getCleanupMinutes() == null ? 15 : cinema.getCleanupMinutes();
        Session s = new Session();
        s.setCinemaId(req.getCinemaId());
        s.setHallId(req.getHallId());
        s.setMovieId(req.getMovieId());
        s.setStartTime(req.getStartTime().withNano(0));
        s.setEndTime(req.getStartTime().withNano(0).plusMinutes(movie.getDurationMinutes()).plusMinutes(cleanup));
        s.setBasePrice(req.getBasePrice());
        s.setPriceVersion(req.getPriceVersion() == null ? priceRuleService.latestVersion() : req.getPriceVersion());
        s.setStatus(SessionStatus.ON_SALE.getCode());
        s.setRebuilding(0);
        return s;
    }

    /** 批内自检：避免同一批数据自己跟自己冲突。 */
    private void selfCheck(List<Session> sessions) {
        Map<Long, List<Session>> byHall = new LinkedHashMap<>();
        for (Session s : sessions) {
            byHall.computeIfAbsent(s.getHallId(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<Long, List<Session>> e : byHall.entrySet()) {
            List<Session> list = e.getValue();
            list.sort(Comparator.comparing(Session::getStartTime));
            for (int i = 1; i < list.size(); i++) {
                Session prev = list.get(i - 1);
                Session cur = list.get(i);
                if (prev.getEndTime().isAfter(cur.getStartTime())) {
                    throw new BizException("批内自检失败：影厅 " + e.getKey()
                            + " 的 " + prev.getStartTime() + " 场次与 " + cur.getStartTime() + " 场次重叠");
                }
            }
        }
    }

    private String describe(List<Session> conflicts) {
        StringBuilder sb = new StringBuilder();
        for (Session c : conflicts) {
            sb.append("[id=").append(c.getId())
                    .append(' ').append(c.getStartTime().toLocalTime()).append('-').append(c.getEndTime().toLocalTime())
                    .append(']');
        }
        return sb.toString();
    }

    /** 供内部计算使用：某日 00:00。 */
    public static LocalDateTime dayStart(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIDNIGHT);
    }
}
