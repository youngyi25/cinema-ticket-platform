package com.xinghe.cinema.cinema.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.cinema.entity.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HallMapper extends BaseMapper<Hall> {

    /** 排片插入时锁住影厅行，作为「同一影厅不能并发排片」的 DB 级备选方案（文档 5.3）。 */
    @Select("SELECT id FROM hall WHERE id = #{hallId} FOR UPDATE")
    Long lockById(Long hallId);
}
