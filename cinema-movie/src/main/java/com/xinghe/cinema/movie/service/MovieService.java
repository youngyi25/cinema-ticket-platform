package com.xinghe.cinema.movie.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.movie.entity.Movie;
import com.xinghe.cinema.movie.mapper.MovieMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 影片服务：上下架与基础信息维护。
 */
@Service
public class MovieService {

    private final MovieMapper movieMapper;

    public MovieService(MovieMapper movieMapper) {
        this.movieMapper = movieMapper;
    }

    public Movie getRequired(Long id) {
        Movie m = movieMapper.selectById(id);
        if (m == null) {
            throw new BizException("影片不存在: " + id);
        }
        return m;
    }

    /** 排片时只允许使用已上架影片。 */
    public Movie getOnShelfRequired(Long id) {
        Movie m = getRequired(id);
        if (m.getStatus() == null || m.getStatus() != 1) {
            throw new BizException("影片已下架: " + m.getName());
        }
        if (m.getDurationMinutes() == null || m.getDurationMinutes() <= 0) {
            throw new BizException("影片片长未配置: " + m.getName());
        }
        return m;
    }

    public List<Movie> listOnShelf() {
        return movieMapper.selectList(Wrappers.<Movie>lambdaQuery().eq(Movie::getStatus, 1)
                .orderByDesc(Movie::getId));
    }

    public List<Movie> listAll() {
        return movieMapper.selectList(Wrappers.<Movie>lambdaQuery().orderByDesc(Movie::getId));
    }

    public Movie create(Movie movie) {
        movie.setId(null);
        if (movie.getStatus() == null) {
            movie.setStatus(1);
        }
        movie.setCreatedAt(LocalDateTime.now());
        movieMapper.insert(movie);
        return movie;
    }

    public void updateStatus(Long id, Integer status) {
        Movie m = getRequired(id);
        m.setStatus(status);
        movieMapper.updateById(m);
    }
}
