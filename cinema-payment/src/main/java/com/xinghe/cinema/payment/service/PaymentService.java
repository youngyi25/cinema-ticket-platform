package com.xinghe.cinema.payment.service;

import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.payment.callback.PaymentCallbackHandler;
import com.xinghe.cinema.payment.entity.Payment;
import com.xinghe.cinema.payment.mapper.PaymentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付服务。
 *
 * <p>没有真实渠道时用 MOCK 通道：下单时创建支付单，{@code /payment/{orderNo}/pay} 模拟渠道回调成功。
 * 回调处理与真实渠道走同一条路径（{@link #handleCallback}），所以「支付与释放撞车」的验证
 * 在 mock 下同样有效。</p>
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ObjectProvider<PaymentCallbackHandler> handlerProvider;

    public PaymentService(PaymentMapper paymentMapper, ObjectProvider<PaymentCallbackHandler> handlerProvider) {
        this.paymentMapper = paymentMapper;
        this.handlerProvider = handlerProvider;
    }

    /** 下单时创建支付单（待支付）。 */
    public Payment createForOrder(Long orderId, String orderNo, BigDecimal amount) {
        Payment p = new Payment();
        p.setPayNo("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        p.setOrderId(orderId);
        p.setOrderNo(orderNo);
        p.setAmount(amount);
        p.setChannel("MOCK");
        p.setStatus(0);
        p.setCreatedAt(LocalDateTime.now());
        paymentMapper.insert(p);
        return p;
    }

    public Payment getByOrderNo(String orderNo) {
        Payment p = paymentMapper.findLatestByOrderNo(orderNo);
        if (p == null) {
            throw new BizException("支付单不存在: " + orderNo);
        }
        return p;
    }

    public Payment getByPayNo(String payNo) {
        Payment p = paymentMapper.findByPayNo(payNo);
        if (p == null) {
            throw new BizException("支付单不存在: " + payNo);
        }
        return p;
    }

    /**
     * 渠道回调统一入口。
     * 幂等：payment 已是成功状态时直接返回成功，不重复驱动订单。
     */
    public PaymentCallbackHandler.CallbackResult handleCallback(String orderNo, String payNo,
                                                                BigDecimal amount, String channel) {
        Payment payment = paymentMapper.findLatestByOrderNo(orderNo);
        if (payment == null) {
            return PaymentCallbackHandler.CallbackResult.fail("支付单不存在: " + orderNo);
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            log.warn("回调金额不一致 orderNo={} expect={} actual={}", orderNo, payment.getAmount(), amount);
            return PaymentCallbackHandler.CallbackResult.fail("回调金额与支付单不一致");
        }
        if (payment.getStatus() != null && payment.getStatus() == 1) {
            return PaymentCallbackHandler.CallbackResult.ok("重复回调，已处理");
        }

        // 若回调携带的 payNo 与本地不一致，以回调为准（渠道流水号）
        String effectivePayNo = payNo == null || payNo.isBlank() ? payment.getPayNo() : payNo;
        int rows = paymentMapper.markSuccess(payment.getPayNo());
        if (rows == 0) {
            return PaymentCallbackHandler.CallbackResult.ok("重复回调，已处理");
        }
        if (channel != null && !channel.isBlank()) {
            Payment update = new Payment();
            update.setId(payment.getId());
            update.setChannel(channel);
            paymentMapper.updateById(update);
        }

        PaymentCallbackHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            throw new BizException("支付回调处理器未装配");
        }
        return handler.onPaySuccess(orderNo, effectivePayNo, amount);
    }

    /** 模拟渠道：直接以本地支付单号回调。 */
    public PaymentCallbackHandler.CallbackResult mockPay(String orderNo) {
        Payment p = getByOrderNo(orderNo);
        return handleCallback(orderNo, p.getPayNo(), p.getAmount(), "MOCK");
    }
}
