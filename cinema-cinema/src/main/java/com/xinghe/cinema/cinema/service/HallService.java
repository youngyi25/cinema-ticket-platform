package com.xinghe.cinema.cinema.service;

import com.xinghe.cinema.cinema.dto.HallCreateRequest;
import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.mapper.HallMapper;
import com.xinghe.cinema.cinema.mapper.SeatMapper;
import com.xinghe.cinema.common.enums.HallType;
import com.xinghe.cinema.common.enums.SeatType;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.common.util.SeatBitmapUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 影厅与座位。建厅时一次性生成座位行（座位是静态基础数据，与「场次座位」不同，后者不预生成）。
 */
@Service
public class HallService {

    private final HallMapper hallMapper;
    private final SeatMapper seatMapper;
    private final CinemaService cinemaService;

    public HallService(HallMapper hallMapper, SeatMapper seatMapper, CinemaService cinemaService) {
        this.hallMapper = hallMapper;
        this.seatMapper = seatMapper;
        this.cinemaService = cinemaService;
    }

    public Hall getRequired(Long hallId) {
        Hall h = hallMapper.selectById(hallId);
        if (h == null) {
            throw new BizException("影厅不存在: " + hallId);
        }
        return h;
    }

    @Transactional(rollbackFor = Exception.class)
    public Hall createHall(HallCreateRequest req) {
        Cinema cinema = cinemaService.getRequired(req.getCinemaId());
        if (cinema.getStatus() == null || cinema.getStatus() != 1) {
            throw new BizException("影院已停业，不能建厅");
        }
        if (req.getColCount() > SeatBitmapUtil.MAX_COL_PER_WORD) {
            throw new BizException("单排座位数不能超过 " + SeatBitmapUtil.MAX_COL_PER_WORD);
        }

        Hall hall = new Hall();
        hall.setCinemaId(req.getCinemaId());
        hall.setName(req.getName());
        hall.setHallType(req.getHallType() == null ? HallType.NORMAL.getCode() : req.getHallType());
        hall.setRowCount(req.getRowCount());
        hall.setColCount(req.getColCount());
        hall.setStatus(1);
        hallMapper.insert(hall);

        seatMapper.insertBatch(buildSeats(hall));
        return hall;
    }

    /**
     * 座位生成规则（文档 7.3 座位分区）：
     * 两侧 edge 列 → C 区（便宜）；中间区域中，前排 30% 与后排 25% → A 区，其余 → B 区（最贵）。
     * 情侣厅所有座位标记 seat_type=2。
     */
    private List<Seat> buildSeats(Hall hall) {
        int rows = hall.getRowCount();
        int cols = hall.getColCount();
        int edge = Math.max(1, cols / 5);
        int aEnd = (int) Math.ceil(rows * 0.3);
        int bEnd = (int) Math.ceil(rows * 0.75);
        boolean coupleHall = HallType.COUPLE.getCode() == hall.getHallType();

        List<Seat> seats = new ArrayList<>(rows * cols);
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                Seat s = new Seat();
                s.setHallId(hall.getId());
                s.setRowNo(r);
                s.setColNo(c);
                s.setSeatType(coupleHall ? SeatType.COUPLE.getCode() : SeatType.NORMAL.getCode());
                s.setAreaCode(resolveArea(r, c, rows, cols, edge, aEnd, bEnd));
                seats.add(s);
            }
        }
        return seats;
    }

    private String resolveArea(int row, int col, int rows, int cols, int edge, int aEnd, int bEnd) {
        if (col <= edge || col > cols - edge) {
            return "C";
        }
        if (row <= aEnd) {
            return "A";
        }
        if (row <= bEnd) {
            return "B";
        }
        return "A";
    }

    public List<Seat> listSeats(Long hallId) {
        return seatMapper.listByHall(hallId);
    }
}
