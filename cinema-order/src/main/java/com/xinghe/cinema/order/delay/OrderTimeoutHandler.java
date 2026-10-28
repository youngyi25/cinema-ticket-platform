package com.xinghe.cinema.order.delay;

import com.xinghe.cinema.common.constant.CinemaConstants;
import com.xinghe.cinema.common.delay.DelayMessageHandler;
import com.xinghe.cinema.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单锁座超时释放的消费者（替代 RocketMQ 消费者）。
 */
@Slf4j
@Component
public class OrderTimeoutHandler implements DelayMessageHandler {

    private final OrderService orderService;

    public OrderTimeoutHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String topic() {
        return CinemaConstants.TOPIC_ORDER_TIMEOUT;
    }

    @Override
    public void handle(String payload) {
        boolean released = orderService.releaseIfExpired(payload);
        log.info("延迟消息处理完成 orderNo={} released={}", payload, released);
    }
}
