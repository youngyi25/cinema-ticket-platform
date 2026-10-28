package com.xinghe.cinema.cinema.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.mapper.CinemaMapper;
import com.xinghe.cinema.cinema.mapper.HallMapper;
import com.xinghe.cinema.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 影院服务。
 */
@Service
public class CinemaService {

    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;

    public CinemaService(CinemaMapper cinemaMapper, HallMapper hallMapper) {
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
    }

    public Cinema getRequired(Long id) {
        Cinema c = cinemaMapper.selectById(id);
        if (c == null) {
            throw new BizException("影院不存在: " + id);
        }
        return c;
    }

    public List<Cinema> listAll() {
        return cinemaMapper.selectList(Wrappers.<Cinema>lambdaQuery().orderByAsc(Cinema::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Cinema create(Cinema cinema) {
        cinema.setId(null);
        if (cinema.getStatus() == null) {
            cinema.setStatus(1);
        }
        if (cinema.getCleanupMinutes() == null) {
            cinema.setCleanupMinutes(15);
        }
        cinema.setCreatedAt(LocalDateTime.now());
        cinemaMapper.insert(cinema);
        return cinema;
    }

    public void updateStatus(Long id, Integer status) {
        Cinema c = getRequired(id);
        c.setStatus(status);
        cinemaMapper.updateById(c);
    }

    public List<Hall> listHalls(Long cinemaId) {
        return hallMapper.selectList(Wrappers.<Hall>lambdaQuery()
                .eq(Hall::getCinemaId, cinemaId)
                .orderByAsc(Hall::getId));
    }
}
