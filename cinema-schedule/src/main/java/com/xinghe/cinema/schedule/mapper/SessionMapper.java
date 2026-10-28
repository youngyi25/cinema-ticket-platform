package com.xinghe.cinema.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.schedule.entity.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionMapper extends BaseMapper<Session> {

    /**
     * 区间重叠查询（文档 5.2）：[s1,e1) 与 [s2,e2) 重叠的充要条件是 s1 < e2 AND s2 < e1。
     */
    @Select("SELECT * FROM `session` " +
            "WHERE hall_id = #{hallId} AND status != -1 " +
            "AND start_time < #{newEnd} AND end_time > #{newStart} " +
            "ORDER BY start_time")
    List<Session> findConflicts(@Param("hallId") Long hallId,
                                @Param("newStart") LocalDateTime newStart,
                                @Param("newEnd") LocalDateTime newEnd);

    /** 指定时间之后同影厅的有效场次，用于批量导入批内自检。 */
    @Select("SELECT * FROM `session` " +
            "WHERE hall_id = #{hallId} AND status != -1 AND start_time >= #{from} " +
            "ORDER BY start_time")
    List<Session> listFrom(@Param("hallId") Long hallId, @Param("from") LocalDateTime from);

    @Update("UPDATE `session` SET rebuilding = #{flag} WHERE id = #{sessionId}")
    int markRebuilding(@Param("sessionId") Long sessionId, @Param("flag") int flag);

    @Select("SELECT * FROM `session` WHERE movie_id = #{movieId} AND start_time >= #{from} " +
            "AND status = 1 ORDER BY start_time")
    List<Session> listSellableByMovie(@Param("movieId") Long movieId, @Param("from") LocalDateTime from);
}
