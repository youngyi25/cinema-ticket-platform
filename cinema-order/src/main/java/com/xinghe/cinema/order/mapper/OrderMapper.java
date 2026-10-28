package com.xinghe.cinema.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单 Mapper。
 *
 * <p>所有状态流转都是「带原状态条件的 UPDATE」（CAS），文档 4.2 / 6.4 的硬性要求：
 * 禁止无条件 UPDATE status。</p>
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM `order` WHERE order_no = #{orderNo}")
    Order findByOrderNo(String orderNo);

    @Select("SELECT * FROM `order` WHERE member_id = #{memberId} AND request_id = #{requestId}")
    Order findByRequestId(@Param("memberId") Long memberId, @Param("requestId") String requestId);

    @Select("SELECT COUNT(*) FROM `order` WHERE member_id = #{memberId} AND session_id = #{sessionId} AND status = 10")
    int countUnpaid(@Param("memberId") Long memberId, @Param("sessionId") Long sessionId);

    /** 支付回调：10 → 20。affectedRows=0 说明订单已被取消或已支付。 */
    @Update("UPDATE `order` SET status = 20, pay_no = #{payNo}, paid_at = NOW(), version = version + 1 " +
            "WHERE id = #{orderId} AND status = 10")
    int markPaid(@Param("orderId") Long orderId, @Param("payNo") String payNo);

    /** 出票：20 → 30。 */
    @Update("UPDATE `order` SET status = 30, version = version + 1 WHERE id = #{orderId} AND status = 20")
    int markIssued(Long orderId);

    /** 超时释放：10 → 90，且必须已过期（文档 4.2）。 */
    @Update("UPDATE `order` SET status = 90, version = version + 1 " +
            "WHERE id = #{orderId} AND status = 10 AND lock_expire_at < NOW()")
    int cancelIfExpired(Long orderId);

    /** 用户主动取消：10 → 90。 */
    @Update("UPDATE `order` SET status = 90, version = version + 1 WHERE id = #{orderId} AND status = 10")
    int cancel(Long orderId);

    /** 申请退票：20/30 → 50。 */
    @Update("UPDATE `order` SET status = 50, version = version + 1 WHERE id = #{orderId} AND status IN (20, 30)")
    int markRefunding(Long orderId);

    /** 退款完成：50 → 60。 */
    @Update("UPDATE `order` SET status = 60, version = version + 1 WHERE id = #{orderId} AND status = 50")
    int markRefunded(Long orderId);

    /** 改签生效：20/30 → 70，同时把订单上的场次/座位/金额换成新值。 */
    @Update("UPDATE `order` SET status = 70, session_id = #{sessionId}, seat_ids = #{seatIds}, " +
            "seat_labels = #{seatLabels}, total_amount = #{totalAmount}, pay_amount = #{payAmount}, version = version + 1 " +
            "WHERE id = #{orderId} AND status IN (20, 30)")
    int markChanged(@Param("orderId") Long orderId,
                    @Param("sessionId") Long sessionId,
                    @Param("seatIds") String seatIds,
                    @Param("seatLabels") String seatLabels,
                    @Param("totalAmount") BigDecimal totalAmount,
                    @Param("payAmount") BigDecimal payAmount);

    /** 扫表兜底：文档 4.1 的 SQL，加 1 分钟偏移避免与主通道同时打同一批订单。 */
    @Select("SELECT * FROM `order` WHERE status = 10 AND lock_expire_at < NOW() - INTERVAL '1' MINUTE " +
            "ORDER BY id LIMIT #{limit}")
    List<Order> findExpired(int limit);

    @Select("SELECT * FROM `order` WHERE order_no LIKE CONCAT('%', #{keyword}, '%') ORDER BY id DESC LIMIT 50")
    List<Order> searchByNo(String keyword);
}
