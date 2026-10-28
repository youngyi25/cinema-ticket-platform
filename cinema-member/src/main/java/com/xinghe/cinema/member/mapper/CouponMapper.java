package com.xinghe.cinema.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.member.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /** 核销优惠券：CAS 防止并发重复使用。 */
    @Update("UPDATE coupon SET status = 1, used_order_id = #{orderId} WHERE id = #{couponId} AND status = 0")
    int markUsed(@Param("couponId") Long couponId, @Param("orderId") Long orderId);

    /** 订单取消/退票时退回优惠券。 */
    @Update("UPDATE coupon SET status = 0, used_order_id = NULL WHERE id = #{couponId} AND used_order_id = #{orderId}")
    int release(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
}
