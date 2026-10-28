package com.xinghe.cinema.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.order.entity.DelayTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DelayTaskMapper extends BaseMapper<DelayTask> {

    /** 到期未执行的任务（含进程重启后的补偿）。 */
    @Select("SELECT * FROM delay_task WHERE status = 0 AND execute_at <= NOW() ORDER BY execute_at LIMIT 200")
    List<DelayTask> findDue();

    @Update("UPDATE delay_task SET status = 1 WHERE id = #{id}")
    int markDone(Long id);

    @Update("UPDATE delay_task SET status = #{status}, retries = retries + 1 WHERE id = #{id}")
    int markResult(DelayTask task);
}
