package com.xinghe.cinema.common.delay;

/**
 * 延迟消息消费者。实现类注册到 LocalDelayMessagePublisher 后按 topic 分发。
 */
public interface DelayMessageHandler {

    /** 订阅的 topic。 */
    String topic();

    /** 消息到达时执行。抛异常会触发重试（最多 3 次）。 */
    void handle(String payload);
}
