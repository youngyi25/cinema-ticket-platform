package com.xinghe.cinema.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场次（文档 1.4）。
 * end_time 已包含清场时间：end_time = start_time + 片长 + cinema.cleanup_minutes，
 * 这样冲突检测只需比较一个字段，无需每次 JOIN 影院表（文档 5.1）。
 */
@Data
@TableName("`session`")
public class Session {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cinemaId;

    private Long hallId;

    private Long movieId;

    private LocalDateTime startTime;

    /** 含清场的结束时间。 */
    private LocalDateTime endTime;

    private BigDecimal basePrice;

    /** 绑定价格配置版本（文档 7.1）。 */
    private Long priceVersion;

    /** 1 可售 / 0 停售 / -1 已取消。 */
    private Integer status;

    /** bitmap 重建中标记（文档 4.5）：重建期间该场次停售，避免重复售出。 */
    private Integer rebuilding;
}
