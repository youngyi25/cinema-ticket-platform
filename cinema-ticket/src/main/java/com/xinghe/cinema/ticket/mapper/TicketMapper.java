package com.xinghe.cinema.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.ticket.dto.SeatOccRow;
import com.xinghe.cinema.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    @Select("SELECT * FROM ticket WHERE order_id = #{orderId}")
    List<Ticket> listByOrder(Long orderId);

    @Select("SELECT seat_id FROM ticket WHERE session_id = #{sessionId}")
    List<Long> listSoldSeatIds(Long sessionId);

    /** 是否有其他订单已占到该座位（防误释放）。 */
    @Select("SELECT COUNT(*) FROM ticket WHERE session_id = #{sessionId} AND seat_id = #{seatId}")
    int countSold(Long sessionId, Long seatId);

    @Delete("DELETE FROM ticket WHERE order_id = #{orderId}")
    int deleteByOrder(Long orderId);

    /**
     * bitmap 重建数据源（文档 4.5）：所有未开场且已售的座位。
     */
    @Select("SELECT t.session_id AS sessionId, s.row_no AS rowNo, s.col_no AS colNo, h.col_count AS colCount " +
            "FROM ticket t " +
            "JOIN `session` ss ON ss.id = t.session_id " +
            "JOIN seat s ON s.id = t.seat_id " +
            "JOIN hall h ON h.id = ss.hall_id " +
            "WHERE ss.start_time > NOW() AND ss.status = 1")
    List<SeatOccRow> selectSoldSeatPositions();

    /**
     * 重建时并入「在途未支付订单」占用的座位，避免把尚未超时的锁直接抹掉造成超卖。
     * 这是对文档 4.5 的补充（文档只重建已售）。
     */
    @Select("SELECT oi.session_id AS sessionId, s.row_no AS rowNo, s.col_no AS colNo, h.col_count AS colCount " +
            "FROM order_item oi " +
            "JOIN `order` o ON o.id = oi.order_id " +
            "JOIN seat s ON s.id = oi.seat_id " +
            "JOIN hall h ON h.id = s.hall_id " +
            "WHERE o.status = 10 AND o.lock_expire_at > NOW()")
    List<SeatOccRow> selectHeldSeatPositions();
}
