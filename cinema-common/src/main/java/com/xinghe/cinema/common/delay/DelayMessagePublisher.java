package com.xinghe.cinema.common.delay;

import java.time.Duration;

/**
 * 延迟消息发布抽象（文档 4.1 主通道）。
 *
 * <p>生产实现应为 RocketMQ 延迟消息；本机无 RocketMQ 时由
 * {@code LocalDelayMessagePublisher} 提供「落库 + JVM 延迟队列 + 启动补偿」的等价语义。
 * 切换实现只需换一个 bean，业务代码零改动。</p>
 */
public interface DelayMessagePublisher {

    /**
     * 发布延迟消息。
     *
     * @param topic   主题，见 {@code CinemaConstants.TOPIC_*}
     * @param payload 业务负载（JSON 或纯文本 id）
     * @param delay   延迟时长
     */
    void publish(String topic, String payload, Duration delay);
}
