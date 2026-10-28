package com.xinghe.cinema.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 关键校验 SQL（文档 8.2），用于压测后与日常对账。
 */
@Mapper
public interface ReconcileMapper {

    /** 1. 是否有重复售出（应为 0）。 */
    @Select("SELECT COUNT(*) FROM (SELECT session_id, seat_id FROM ticket " +
            "GROUP BY session_id, seat_id HAVING COUNT(*) > 1) t")
    int countDuplicateSales();

    /** 2. 是否有已支付但座位未出票（应为 0）。 */
    @Select("SELECT o.id FROM `order` o LEFT JOIN ticket t ON t.order_id = o.id " +
            "WHERE o.status IN (20, 30) AND t.id IS NULL")
    List<Long> paidWithoutTicket();

    /** 3. 是否有超时未释放（检查任务是否积压）。 */
    @Select("SELECT COUNT(*) FROM `order` WHERE status = 10 AND lock_expire_at < NOW() - INTERVAL '5' MINUTE")
    int countOverdueUnreleased();
}
