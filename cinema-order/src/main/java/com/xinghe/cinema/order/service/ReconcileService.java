package com.xinghe.cinema.order.service;

import com.xinghe.cinema.order.mapper.ReconcileMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账检查（文档 8.2）。正常运行时三项必须为 0 / 空。
 */
@Service
public class ReconcileService {

    private final ReconcileMapper reconcileMapper;

    public ReconcileService(ReconcileMapper reconcileMapper) {
        this.reconcileMapper = reconcileMapper;
    }

    public Map<String, Object> check() {
        Map<String, Object> r = new LinkedHashMap<>();
        int dup = reconcileMapper.countDuplicateSales();
        List<Long> paidWithoutTicket = reconcileMapper.paidWithoutTicket();
        int overdue = reconcileMapper.countOverdueUnreleased();

        r.put("duplicateSales", dup);
        r.put("paidWithoutTicket", paidWithoutTicket);
        r.put("overdueUnreleased", overdue);
        r.put("healthy", dup == 0 && paidWithoutTicket.isEmpty() && overdue == 0);
        return r;
    }
}
