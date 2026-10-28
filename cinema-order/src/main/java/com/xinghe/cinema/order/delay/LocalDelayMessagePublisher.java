package com.xinghe.cinema.order.delay;

import com.xinghe.cinema.common.delay.DelayMessageHandler;
import com.xinghe.cinema.common.delay.DelayMessagePublisher;
import com.xinghe.cinema.order.entity.DelayTask;
import com.xinghe.cinema.order.mapper.DelayTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 本地延迟消息实现（替代 RocketMQ 延迟消息，保证本机无 MQ 也能完整跑通链路）。
 *
 * <p>语义对齐：</p>
 * <ul>
 *   <li>消息落 delay_task 表 —— 进程重启不丢；</li>
 *   <li>JVM 内 ScheduledExecutorService 准时触发 —— 对应 MQ 的定时投递；</li>
 *   <li>定时扫描到期任务 —— 对应消费失败/进程退出后的补偿；</li>
 *   <li>失败重试 3 次，超限置为 2（失败）。</li>
 * </ul>
 *
 * <p>切到 RocketMQ 只需再提供一个 DelayMessagePublisher 实现并去掉 @Primary。</p>
 */
@Slf4j
@Component
public class LocalDelayMessagePublisher implements DelayMessagePublisher {

    private static final int MAX_RETRY = 3;

    private final DelayTaskMapper delayTaskMapper;
    private final ObjectProvider<DelayMessageHandler> handlerProvider;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public LocalDelayMessagePublisher(DelayTaskMapper delayTaskMapper,
                                      ObjectProvider<DelayMessageHandler> handlerProvider) {
        this.delayTaskMapper = delayTaskMapper;
        this.handlerProvider = handlerProvider;
    }

    @Override
    public void publish(String topic, String payload, Duration delay) {
        DelayTask task = new DelayTask();
        task.setTopic(topic);
        task.setPayload(payload);
        task.setExecuteAt(LocalDateTime.now().plus(delay));
        task.setStatus(0);
        task.setRetries(0);
        task.setCreatedAt(LocalDateTime.now());
        delayTaskMapper.insert(task);

        scheduler.schedule(() -> dispatch(task.getId()), Math.max(delay.toMillis(), 0), TimeUnit.MILLISECONDS);
        log.info("延迟消息入队 topic={} payload={} delay={}s", topic, payload, delay.toSeconds());
    }

    /** 到期任务补偿扫描：进程重启、线程池丢任务时兜底。 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void recover() {
        List<DelayTask> due = delayTaskMapper.findDue();
        if (due.isEmpty()) {
            return;
        }
        log.info("补偿到期延迟任务 {} 条", due.size());
        for (DelayTask task : due) {
            dispatch(task.getId());
        }
    }

    private void dispatch(Long taskId) {
        DelayTask task = delayTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() == null || task.getStatus() != 0) {
            return;
        }
        Map<String, DelayMessageHandler> handlers = handlerProvider.stream()
                .collect(Collectors.toMap(DelayMessageHandler::topic, h -> h, (a, b) -> a));
        DelayMessageHandler handler = handlers.get(task.getTopic());
        if (handler == null) {
            log.warn("没有消费者订阅 topic={}，任务 {} 置为失败", task.getTopic(), taskId);
            DelayTask update = new DelayTask();
            update.setId(taskId);
            update.setStatus(2);
            delayTaskMapper.markResult(update);
            return;
        }
        try {
            handler.handle(task.getPayload());
            delayTaskMapper.markDone(taskId);
        } catch (Exception e) {
            log.error("延迟任务执行失败 topic={} payload={} retries={}", task.getTopic(), task.getPayload(),
                    task.getRetries(), e);
            boolean giveUp = task.getRetries() != null && task.getRetries() + 1 >= MAX_RETRY;
            DelayTask update = new DelayTask();
            update.setId(taskId);
            update.setStatus(giveUp ? 2 : 0);
            delayTaskMapper.markResult(update);
            if (!giveUp) {
                scheduler.schedule(() -> dispatch(taskId), 5, TimeUnit.SECONDS);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
