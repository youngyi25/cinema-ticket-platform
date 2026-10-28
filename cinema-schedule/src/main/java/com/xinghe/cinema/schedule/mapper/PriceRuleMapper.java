package com.xinghe.cinema.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinghe.cinema.schedule.entity.PriceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PriceRuleMapper extends BaseMapper<PriceRule> {

    @Select("SELECT * FROM price_rule WHERE version = #{version}")
    List<PriceRule> listByVersion(Long version);

    @Select("SELECT * FROM price_rule WHERE version = #{version} AND day_type = #{dayType} " +
            "AND time_slot = #{timeSlot} AND hall_type = #{hallType} AND area_code = #{areaCode}")
    PriceRule findOne(@Param("version") Long version,
                      @Param("dayType") Integer dayType,
                      @Param("timeSlot") String timeSlot,
                      @Param("hallType") Integer hallType,
                      @Param("areaCode") String areaCode);

    @Select("SELECT COALESCE(MAX(version), 0) FROM price_rule")
    long maxVersion();

    @Select("SELECT DISTINCT version FROM price_rule ORDER BY version DESC")
    java.util.List<Long> listVersions();
}
