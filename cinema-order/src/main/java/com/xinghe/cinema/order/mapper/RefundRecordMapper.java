package com.xinghe.cinema.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.order.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    @Select("SELECT * FROM refund_record WHERE order_no = #{orderNo} ORDER BY id DESC")
    List<RefundRecord> listByOrderNo(String orderNo);

    @Update("UPDATE refund_record SET status = #{status} WHERE id = #{id}")
    int updateStatus(RefundRecord record);
}
