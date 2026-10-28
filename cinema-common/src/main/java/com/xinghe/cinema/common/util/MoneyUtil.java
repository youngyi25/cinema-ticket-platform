package com.xinghe.cinema.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具：统一保留 2 位小数、四舍五入。
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    public static BigDecimal scale2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 向下取整到分（退票金额计算用，文档 6.1）。 */
    public static BigDecimal floor2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.DOWN)
                : v.setScale(2, RoundingMode.DOWN);
    }

    public static BigDecimal multiply(BigDecimal base, BigDecimal... factors) {
        BigDecimal r = base == null ? BigDecimal.ZERO : base;
        for (BigDecimal f : factors) {
            r = r.multiply(f == null ? BigDecimal.ONE : f);
        }
        return scale2(r);
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal r = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v != null) {
                r = r.add(v);
            }
        }
        return scale2(r);
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
