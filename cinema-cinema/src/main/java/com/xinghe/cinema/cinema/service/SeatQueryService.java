package com.xinghe.cinema.cinema.service;

import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.mapper.SeatMapper;
import com.xinghe.cinema.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 座位查询（供 ticket / order 复用）。
 */
@Service
public class SeatQueryService {

    private final SeatMapper seatMapper;

    public SeatQueryService(SeatMapper seatMapper) {
        this.seatMapper = seatMapper;
    }

    public List<Seat> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 去重后查询：入参重复不应被当成"座位不存在"
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(ids));
        List<Seat> seats = seatMapper.selectBatchIds(distinct);
        if (seats.size() != distinct.size()) {
            throw new BizException("存在无效座位 id");
        }
        return seats;
    }

    public Map<Long, Seat> mapByIds(Collection<Long> ids) {
        Map<Long, Seat> map = new HashMap<>();
        for (Seat s : listByIds(ids)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    public List<Seat> listByHall(Long hallId) {
        return seatMapper.listByHall(hallId);
    }
}
