package com.xinghe.cinema.schedule.service;

import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.schedule.entity.PriceRule;
import com.xinghe.cinema.schedule.mapper.PriceRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 价格规则版本管理（文档 7.1）。
 * 调价生成新版本，老场次仍绑定老版本，已售票金额不变。
 */
@Service
public class PriceRuleService {

    private final PriceRuleMapper priceRuleMapper;

    public PriceRuleService(PriceRuleMapper priceRuleMapper) {
        this.priceRuleMapper = priceRuleMapper;
    }

    public long latestVersion() {
        return priceRuleMapper.maxVersion();
    }

    /**
     * 用给定规则集创建一个新版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createVersion(List<PriceRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BizException("价格规则不能为空");
        }
        long version = priceRuleMapper.maxVersion() + 1;
        for (PriceRule rule : rules) {
            if (rule.getMultiplier() == null || rule.getMultiplier().signum() <= 0) {
                throw new BizException("系数必须大于 0");
            }
            rule.setId(null);
            rule.setVersion(version);
            if (rule.getDayType() == null || rule.getTimeSlot() == null
                    || rule.getHallType() == null || rule.getAreaCode() == null) {
                throw new BizException("规则维度不完整: " + rule);
            }
            priceRuleMapper.insert(rule);
        }
        return version;
    }

    public List<PriceRule> listByVersion(long version) {
        return priceRuleMapper.listByVersion(version);
    }

    public List<Long> listVersions() {
        return priceRuleMapper.listVersions();
    }
}
