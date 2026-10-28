package com.xinghe.cinema.ticket.service;

import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.cinema.service.SeatQueryService;
import com.xinghe.cinema.common.enums.SeatStatus;
import com.xinghe.cinema.common.redis.SeatBitmapRedis;
import com.xinghe.cinema.common.util.SeatBitmapUtil;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.PricingService;
import com.xinghe.cinema.schedule.service.SessionService;
import com.xinghe.cinema.ticket.dto.SeatItemVO;
import com.xinghe.cinema.ticket.dto.SeatMapVO;
import com.xinghe.cinema.ticket.dto.SeatRowVO;
import com.xinghe.cinema.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 座位图查询 + 连座推荐（文档第 3 节）。
 *
 * <p>性能要点：整场 bitmap 一次取回（25 字节左右），全部在本地做位运算，
 * 不做逐座 GETBIT（20 座 = 20 次网络往返）。</p>
 */
@Service
public class SeatMapService {

    private static final int DEFAULT_NEED = 2;
    private static final int MAX_NEED = 6;
    private static final int MAX_RECOMMEND = 3;

    private final SeatBitmapRedis bitmapRedis;
    private final TicketMapper ticketMapper;
    private final SessionService sessionService;
    private final HallService hallService;
    private final SeatQueryService seatQueryService;
    private final PricingService pricingService;

    public SeatMapService(SeatBitmapRedis bitmapRedis, TicketMapper ticketMapper, SessionService sessionService,
                          HallService hallService, SeatQueryService seatQueryService, PricingService pricingService) {
        this.bitmapRedis = bitmapRedis;
        this.ticketMapper = ticketMapper;
        this.sessionService = sessionService;
        this.hallService = hallService;
        this.seatQueryService = seatQueryService;
        this.pricingService = pricingService;
    }

    /**
     * 座位图（文档 3.5）。
     *
     * @param need 连座推荐需要的连续座位数，默认 2
     */
    public SeatMapVO seatMap(Long sessionId, Integer need) {
        int want = need == null ? DEFAULT_NEED : Math.min(Math.max(need, 1), MAX_NEED);
        Session session = sessionService.getRequired(sessionId);
        Hall hall = hallService.getRequired(session.getHallId());

        List<Seat> seats = seatQueryService.listByHall(hall.getId());
        long[] words = loadWords(sessionId, hall);
        Set<Long> soldSeatIds = new HashSet<>(ticketMapper.listSoldSeatIds(sessionId));

        Map<String, BigDecimal> priceByArea = pricingService.previewByArea(
                session, hall.getHallType(), List.of("A", "B", "C"));

        Map<Integer, List<Seat>> byRow = new LinkedHashMap<>();
        for (Seat s : seats) {
            byRow.computeIfAbsent(s.getRowNo(), k -> new ArrayList<>()).add(s);
        }

        List<SeatRowVO> rows = new ArrayList<>(byRow.size());
        List<Candidate> candidates = new ArrayList<>();

        for (Map.Entry<Integer, List<Seat>> entry : byRow.entrySet()) {
            int rowNo = entry.getKey();
            List<Seat> rowSeats = entry.getValue();
            rowSeats.sort(Comparator.comparing(Seat::getColNo));

            int base = SeatBitmapUtil.offset(rowNo, 1, hall.getColCount());
            long occupied = SeatBitmapUtil.extractRow(words, base, hall.getColCount());
            long available = ~occupied & SeatBitmapUtil.rowMask(hall.getColCount());

            collectCandidates(candidates, rowNo, available, hall, want);

            List<SeatItemVO> items = new ArrayList<>(rowSeats.size());
            for (Seat s : rowSeats) {
                SeatItemVO vo = new SeatItemVO();
                vo.setSeatId(s.getId());
                vo.setColNo(s.getColNo());
                vo.setArea(s.getAreaCode());
                vo.setSeatType(s.getSeatType());
                vo.setPrice(priceByArea.getOrDefault(s.getAreaCode(), session.getBasePrice()));

                boolean bitSet = (occupied & (1L << (s.getColNo() - 1))) != 0;
                if (!bitSet) {
                    vo.setStatus(SeatStatus.AVAILABLE.getCode());
                } else if (soldSeatIds.contains(s.getId())) {
                    vo.setStatus(SeatStatus.SOLD.getCode());
                } else {
                    vo.setStatus(SeatStatus.LOCKED.getCode());
                }
                items.add(vo);
            }
            SeatRowVO rowVO = new SeatRowVO();
            rowVO.setRowNo(rowNo);
            rowVO.setSeats(items);
            rows.add(rowVO);
        }

        candidates.sort(Comparator.comparingInt(Candidate::score));

        SeatMapVO vo = new SeatMapVO();
        vo.setSessionId(sessionId);
        vo.setHallId(hall.getId());
        vo.setRowCount(hall.getRowCount());
        vo.setColCount(hall.getColCount());
        vo.setRows(rows);

        List<List<Integer>> recommend = new ArrayList<>();
        List<Integer> recommendRows = new ArrayList<>();
        candidates.stream().limit(MAX_RECOMMEND).forEach(c -> {
            List<Integer> cols = new ArrayList<>(want);
            for (int i = 0; i < want; i++) {
                cols.add(c.startSeat() + i);
            }
            recommend.add(cols);
            recommendRows.add(c.rowNo());
        });
        vo.setRecommend(recommend);
        vo.setRecommendRows(recommendRows);
        return vo;
    }

    /** 取整场位图并转成 word 数组。 */
    public long[] loadWords(Long sessionId, Hall hall) {
        byte[] raw = bitmapRedis.loadRaw(sessionId);
        return SeatBitmapUtil.bytesToWords(raw, hall.seatCount());
    }

    /**
     * 打分排序（文档 3.3）：横向居中优先（权重 2），纵向靠黄金排优先（权重 1），分数越小越优。
     */
    private void collectCandidates(List<Candidate> out, int rowNo, long available, Hall hall, int need) {
        long starts = SeatBitmapUtil.startsOfRun(available, need);
        if (starts == 0L) {
            return;
        }
        int centerCol = (hall.getColCount() + 1) / 2;
        int goldenRow = (hall.getRowCount() + 1) / 2;
        for (int i = 0; i < hall.getColCount(); i++) {
            if ((starts & (1L << i)) == 0) {
                continue;
            }
            int startSeat = i + 1;
            int startCenter = startSeat + need / 2;
            int colScore = Math.abs(startCenter - centerCol);
            int rowScore = Math.abs(rowNo - goldenRow);
            out.add(new Candidate(rowNo, startSeat, colScore * 2 + rowScore));
        }
    }

    private record Candidate(int rowNo, int startSeat, int score) {
    }
}
