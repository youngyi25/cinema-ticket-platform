package com.xinghe.cinema.payment.service;

import com.xinghe.cinema.common.enums.RefundStatus;
import com.xinghe.cinema.payment.entity.Refund;
import com.xinghe.cinema.payment.mapper.RefundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 退款服务。mock 通道下同步置为「退款成功」，真实渠道需接入异步退款通知。
 */
@Slf4j
@Service
public class RefundService {

    private final RefundMapper refundMapper;

    public RefundService(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    /**
     * 发起退款。
     *
     * @param amount 实际退款金额（已扣除手续费）
     * @param fee    手续费
     */
    public Refund refund(Long orderId, String orderNo, BigDecimal amount, BigDecimal fee, String reason) {
        Refund r = new Refund();
        r.setRefundNo("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        r.setOrderId(orderId);
        r.setOrderNo(orderNo);
        r.setAmount(amount);
        r.setFee(fee);
        r.setReason(reason);
        r.setStatus(RefundStatus.SUCCESS.getCode());
        r.setCreatedAt(LocalDateTime.now());
        r.setRefundedAt(LocalDateTime.now());
        refundMapper.insert(r);
        log.info("退款完成 orderNo={} amount={} fee={} reason={}", orderNo, amount, fee, reason);
        return r;
    }

    public List<Refund> listByOrderNo(String orderNo) {
        return refundMapper.listByOrderNo(orderNo);
    }
}
