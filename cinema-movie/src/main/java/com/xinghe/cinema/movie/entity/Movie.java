package com.xinghe.cinema.movie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 影片。文档未给出 DDL，按 movie-service 职责补：片长是排片计算 end_time 的必要字段。
 */
@Data
@TableName("movie")
public class Movie {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 片长（分钟），排片时 end_time = start_time + duration + cleanup_minutes。 */
    private Integer durationMinutes;

    private String director;

    private String actors;

    private String posterUrl;

    private LocalDate releaseDate;

    /** 1 上架 / 0 下架。 */
    private Integer status;

    private LocalDateTime createdAt;
}
