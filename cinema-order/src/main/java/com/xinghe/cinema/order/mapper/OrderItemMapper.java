package com.xinghe.cinema.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.order.entity.OrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> listByOrder(Long orderId);

    @Delete("DELETE FROM order_item WHERE order_id = #{orderId}")
    int deleteByOrder(Long orderId);

    /** 改签：更新明细的场次 / 座位 / 价格。 */
    @Update("UPDATE order_item SET session_id = #{sessionId}, seat_id = #{seatId}, " +
            "seat_label = #{seatLabel}, price = #{price} WHERE id = #{id}")
    int updateForChange(OrderItem item);

    @Select("SELECT COALESCE(SUM(price), 0) FROM order_item WHERE order_id = #{orderId}")
    BigDecimal sumPrice(Long orderId);
}
