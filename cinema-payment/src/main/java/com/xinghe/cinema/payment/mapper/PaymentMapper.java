package com.xinghe.cinema.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT * FROM payment WHERE pay_no = #{payNo}")
    Payment findByPayNo(String payNo);

    @Select("SELECT * FROM payment WHERE order_no = #{orderNo} ORDER BY id DESC LIMIT 1")
    Payment findLatestByOrderNo(String orderNo);

    /** 支付成功的幂等 CAS：只有 0 待支付 才能转 1。 */
    @Update("UPDATE payment SET status = 1, paid_at = NOW() WHERE pay_no = #{payNo} AND status = 0")
    int markSuccess(@Param("payNo") String payNo);
}
