package com.xinghe.cinema.schedule.controller;

import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.schedule.entity.PriceRule;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.PriceRuleService;
import com.xinghe.cinema.schedule.service.PricingService;
import com.xinghe.cinema.schedule.service.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 价格规则与试算接口（文档第 7 节）。
 */
@RestController
@RequestMapping("/price")
public class PriceController {

    private final PriceRuleService priceRuleService;
    private final PricingService pricingService;
    private final SessionService sessionService;
    private final HallService hallService;

    public PriceController(PriceRuleService priceRuleService, PricingService pricingService,
                           SessionService sessionService, HallService hallService) {
        this.priceRuleService = priceRuleService;
        this.pricingService = pricingService;
        this.sessionService = sessionService;
        this.hallService = hallService;
    }

    /** 新建价格版本（调价）。 */
    @PostMapping("/rule/version")
    public Result<Long> createVersion(@RequestBody List<PriceRule> rules) {
        return Result.ok(priceRuleService.createVersion(rules));
    }

    @GetMapping("/rule")
    public Result<List<PriceRule>> rules(@RequestParam(required = false) Long version) {
        long v = version == null ? priceRuleService.latestVersion() : version;
        return Result.ok(priceRuleService.listByVersion(v));
    }

    @GetMapping("/version/list")
    public Result<List<Long>> versions() {
        return Result.ok(priceRuleService.listVersions());
    }

    /** 场次各分区实际票价试算。 */
    @GetMapping("/preview")
    public Result<Map<String, BigDecimal>> preview(@RequestParam Long sessionId) {
        Session session = sessionService.getRequired(sessionId);
        Hall hall = hallService.getRequired(session.getHallId());
        return Result.ok(pricingService.previewByArea(session, hall.getHallType(), List.of("A", "B", "C")));
    }
}
