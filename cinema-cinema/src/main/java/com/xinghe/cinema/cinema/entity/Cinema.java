package com.xinghe.cinema.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影院（文档 1.4）。
 */
@Data
@TableName("cinema")
public class Cinema {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 城市编码。 */
    private String cityCode;

    private String address;

    /** 1 营业 / 0 停业。 */
    private Integer status;

    /** 场间清场时长（分钟）。 */
    private Integer cleanupMinutes;

    private LocalDateTime createdAt;
}
