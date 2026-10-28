package com.xinghe.cinema.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 改签记录（文档 6.2 步骤 3c）。
 * old_released = 0 表示旧座位尚未成功释放（泄漏），由定时任务补清。
 */
@Data
@TableName("change_record")
public class ChangeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long oldSessionId;

    private String oldSeatIds;

    private String oldSeatLabels;

    private Long newSessionId;

    private String newSeatIds;

    private String newSeatLabels;

    /** 新总价 - 旧总价。 */
    private BigDecimal diffAmount;

    /** 1 成功 / 2 待补款 / 3 已回滚。 */
    private Integer status;

    /** 旧座位是否已释放：0 未释放 / 1 已释放。 */
    private Integer oldReleased;

    /** 补款截止时间（仅待补款状态有效）。 */
    private LocalDateTime payExpireAt;

    private LocalDateTime createdAt;
}
