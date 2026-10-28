package com.xinghe.cinema.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.payment.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    @Select("SELECT * FROM refund WHERE order_no = #{orderNo} ORDER BY id DESC")
    List<Refund> listByOrderNo(String orderNo);
}
