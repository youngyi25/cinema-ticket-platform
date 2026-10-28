package com.xinghe.cinema.order.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cinema.order")
public class OrderProperties {

    /** 锁座超时时长（分钟），文档 4.1 默认 15。 */
    private int lockTimeoutMinutes = 15;

    /** 改签补款超时时长（分钟）。 */
    private int changePayTimeoutMinutes = 15;

    /** 扫表兜底单批条数。 */
    private int jobBatchSize = 200;

    /** 扫表兜底开关（压测时可关掉，只走延迟消息）。 */
    private boolean sweepEnabled = true;
}
