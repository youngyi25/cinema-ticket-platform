package com.xinghe.cinema.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.order.entity.ChangeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChangeRecordMapper extends BaseMapper<ChangeRecord> {

    /** 补款超时的待回滚记录。 */
    @Select("SELECT * FROM change_record WHERE status = 2 AND pay_expire_at < NOW() LIMIT 200")
    List<ChangeRecord> findPayTimeout();

    /** 旧座位尚未释放的记录（座位泄漏补偿）。 */
    @Select("SELECT * FROM change_record WHERE status = 1 AND old_released = 0 LIMIT 200")
    List<ChangeRecord> findUnreleased();

    @Update("UPDATE change_record SET old_released = 1 WHERE id = #{id}")
    int markOldReleased(Long id);

    @Update("UPDATE change_record SET status = #{status} WHERE id = #{id} AND status = 2")
    int markPendingStatus(ChangeRecord record);

    @Select("SELECT * FROM change_record WHERE id = #{id}")
    ChangeRecord findById(Long id);

    @Select("SELECT * FROM change_record WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    ChangeRecord findLatestByOrder(Long orderId);
}
