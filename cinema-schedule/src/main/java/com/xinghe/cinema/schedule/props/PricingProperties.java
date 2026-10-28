package com.xinghe.cinema.schedule.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 定价相关配置。节假日无法算法推导，必须配置化（文档 7.1 day_type=3）。
 * 示例：cinema.pricing.holidays=2026-10-01,2026-10-02
 */
@Data
@Component
@ConfigurationProperties(prefix = "cinema.pricing")
public class PricingProperties {

    private List<String> holidays = new ArrayList<>();

    /** 无匹配规则时的兜底系数。 */
    private String defaultMultiplier = "1.0000";
}
