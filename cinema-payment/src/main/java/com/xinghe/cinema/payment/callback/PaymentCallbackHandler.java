package com.xinghe.cinema.payment.callback;

import java.math.BigDecimal;

/**
 * 支付成功回调的业务处理器。由 order-service 实现（支付回调要驱动订单状态与出票）。
 *
 * <p>放在 payment 模块是为了让「回调入口」归 payment-service，而「订单状态变更」归 order-service，
 * 依赖方向保持 order → payment 单向，不出现循环依赖。</p>
 */
public interface PaymentCallbackHandler {

    CallbackResult onPaySuccess(String orderNo, String payNo, BigDecimal amount);

    record CallbackResult(boolean success, String message) {

        public static CallbackResult ok(String message) {
            return new CallbackResult(true, message);
        }

        public static CallbackResult fail(String message) {
            return new CallbackResult(false, message);
        }
    }
}
