package com.xinghe.cinema.schedule.service;

import com.xinghe.cinema.common.enums.DayType;
import com.xinghe.cinema.common.enums.TimeSlot;
import com.xinghe.cinema.common.util.MoneyUtil;
import com.xinghe.cinema.schedule.entity.PriceRule;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.mapper.PriceRuleMapper;
import com.xinghe.cinema.schedule.props.PricingProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态定价（文档第 7 节）。
 *
 * <p>最终票价 = session.base_price × multiplier(day_type, time_slot, hall_type, area_code)。
 * 文档 7.1 的公式把「时段系数 × 厅型系数 × 分区系数」拆成三个乘数，但表结构
 * price_rule 的唯一键把四个维度合成一行、只留一个 multiplier —— 两者等价，
 * 本实现按表结构走（一次查询得到一个综合系数）。</p>
 *
 * <p>价格是快照不是引用：这里算出的值会写死到 order_item.price 与 ticket.price。</p>
 */
@Service
public class PricingService {

    private final PriceRuleMapper priceRuleMapper;
    private final PricingProperties properties;

    public PricingService(PriceRuleMapper priceRuleMapper, PricingProperties properties) {
        this.priceRuleMapper = priceRuleMapper;
        this.properties = properties;
    }

    /** 单个座位的成交单价。 */
    public BigDecimal unitPrice(Session session, Integer hallType, String areaCode) {
        return MoneyUtil.scale2(session.getBasePrice().multiply(multiplier(session, hallType, areaCode)));
    }

    public BigDecimal multiplier(Session session, Integer hallType, String areaCode) {
        Long version = session.getPriceVersion() == null ? 0L : session.getPriceVersion();
        Integer dayType = DayType.of(session.getStartTime().toLocalDate(), holidaySet()).getCode();
        String slot = TimeSlot.of(session.getStartTime().toLocalTime()).getCode();
        PriceRule rule = priceRuleMapper.findOne(version, dayType, slot,
                hallType == null ? 1 : hallType, areaCode == null ? "B" : areaCode);
        if (rule == null || rule.getMultiplier() == null) {
            return new BigDecimal(properties.getDefaultMultiplier());
        }
        return rule.getMultiplier();
    }

    /**
     * 按分区给出预览价，供场次详情页展示「不同区域不同价」。
     */
    public Map<String, BigDecimal> previewByArea(Session session, Integer hallType, List<String> areas) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (String area : areas) {
            map.put(area, unitPrice(session, hallType, area));
        }
        return map;
    }

    private Set<LocalDate> holidaySet() {
        Set<LocalDate> set = new HashSet<>();
        for (String s : properties.getHolidays()) {
            if (StringUtils.hasText(s)) {
                set.add(LocalDate.parse(s.trim()));
            }
        }
        return set;
    }
}
