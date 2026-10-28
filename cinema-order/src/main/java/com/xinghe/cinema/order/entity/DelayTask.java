package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 延迟任务（本地版延迟消息的持久化载体，替代 RocketMQ 延迟消息）。
 * 应用重启后由扫描任务补偿执行，保证「超时释放」不会因为进程退出而永久丢失。
 */
@Data
@TableName("delay_task")
public class DelayTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String topic;

    private String payload;

    private LocalDateTime executeAt;

    /** 0 待执行 / 1 已执行 / 2 执行失败。 */
    private Integer status;

    private Integer retries;

    private LocalDateTime createdAt;
}
