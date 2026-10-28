package com.xinghe.cinema.order.delay;

import com.xinghe.cinema.common.constant.CinemaConstants;
import com.xinghe.cinema.common.delay.DelayMessageHandler;
import com.xinghe.cinema.order.service.OrderChangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 改签补款超时回滚的消费者。
 */
@Slf4j
@Component
public class ChangeTimeoutHandler implements DelayMessageHandler {

    private final OrderChangeService orderChangeService;

    public ChangeTimeoutHandler(OrderChangeService orderChangeService) {
        this.orderChangeService = orderChangeService;
    }

    @Override
    public String topic() {
        return CinemaConstants.TOPIC_CHANGE_TIMEOUT;
    }

    @Override
    public void handle(String payload) {
        orderChangeService.rollbackPending(Long.valueOf(payload));
        log.info("改签补款超时任务处理完成 changeId={}", payload);
    }
}
