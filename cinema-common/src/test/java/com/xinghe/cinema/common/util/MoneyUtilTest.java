package com.xinghe.cinema.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyUtilTest {

    @Test
    void refundAmountFloorsToCent() {
        // 实付 33.33，费率 30% → 33.33 × 0.7 = 23.331 → 向下取整 23.33
        BigDecimal feeRate = new BigDecimal("0.3000");
        BigDecimal refund = MoneyUtil.floor2(new BigDecimal("33.33")
                .multiply(BigDecimal.ONE.subtract(feeRate)));
        assertEquals(new BigDecimal("23.33"), refund);
    }

    @Test
    void multiplyKeepsTwoScale() {
        assertEquals(new BigDecimal("58.50"), MoneyUtil.multiply(new BigDecimal("45.00"),
                new BigDecimal("1.30")));
    }

    @Test
    void sumIgnoresNull() {
        assertEquals(new BigDecimal("45.00"), MoneyUtil.sum(new BigDecimal("45.00"), null));
    }
}
