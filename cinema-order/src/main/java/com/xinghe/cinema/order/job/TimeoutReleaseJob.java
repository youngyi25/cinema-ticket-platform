package com.xinghe.cinema.order.job;

import com.xinghe.cinema.order.entity.ChangeRecord;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.mapper.ChangeRecordMapper;
import com.xinghe.cinema.order.props.OrderProperties;
import com.xinghe.cinema.order.service.OrderChangeService;
import com.xinghe.cinema.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底扫表（替代 XXL-Job，文档 4.1 的「兜底」通道）。
 *
 * <p>SQL 与文档一致：{@code status = 10 AND lock_expire_at < NOW() - INTERVAL 1 MINUTE LIMIT 200}
 * —— 加 1 分钟偏移，避免和主通道（延迟消息）同时打同一批订单。</p>
 *
 * <p>同时兜两类漏：改签补款超时未回滚、改签旧座位释放失败造成的座位泄漏。</p>
 */
@Slf4j
@Component
public class TimeoutReleaseJob {

    private final OrderService orderService;
    private final OrderChangeService orderChangeService;
    private final ChangeRecordMapper changeRecordMapper;
    private final OrderProperties properties;

    public TimeoutReleaseJob(OrderService orderService, OrderChangeService orderChangeService,
                             ChangeRecordMapper changeRecordMapper, OrderProperties properties) {
        this.orderService = orderService;
        this.orderChangeService = orderChangeService;
        this.changeRecordMapper = changeRecordMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void sweep() {
        if (!properties.isSweepEnabled()) {
            return;
        }
        List<Order> expired = orderService.findExpiredBatch(properties.getJobBatchSize());
        int released = 0;
        for (Order order : expired) {
            try {
                if (orderService.releaseIfExpired(order)) {
                    released++;
                }
            } catch (Exception e) {
                log.error("扫表释放订单失败 orderNo={}", order.getOrderNo(), e);
            }
        }

        List<ChangeRecord> payTimeout = changeRecordMapper.findPayTimeout();
        int rolledBack = 0;
        for (ChangeRecord rec : payTimeout) {
            orderChangeService.rollbackPending(rec.getId());
            rolledBack++;
        }

        int fixedLeak = orderChangeService.repairLeakedOldSeats();

        if (released + rolledBack + fixedLeak > 0) {
            log.info("兜底任务：超时释放订单 {} 个，回滚改签 {} 个，补清座位泄漏 {} 个",
                    released, rolledBack, fixedLeak);
        }
    }
}
