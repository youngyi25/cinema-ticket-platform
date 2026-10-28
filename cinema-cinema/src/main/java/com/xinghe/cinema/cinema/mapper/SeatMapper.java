package com.xinghe.cinema.cinema.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.cinema.entity.Seat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    @Select("SELECT * FROM seat WHERE hall_id = #{hallId} ORDER BY row_no, col_no")
    List<Seat> listByHall(Long hallId);

    @Select("SELECT COUNT(*) FROM seat WHERE hall_id = #{hallId}")
    int countByHall(Long hallId);

    @Insert("<script>" +
            "INSERT INTO seat (hall_id, row_no, col_no, seat_type, area_code) VALUES " +
            "<foreach collection='seats' item='s' separator=','>" +
            "(#{s.hallId}, #{s.rowNo}, #{s.colNo}, #{s.seatType}, #{s.areaCode})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("seats") List<Seat> seats);
}
