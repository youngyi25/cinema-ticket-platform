package com.xinghe.cinema.movie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.movie.entity.Movie;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MovieMapper extends BaseMapper<Movie> {
}
