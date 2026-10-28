package com.xinghe.cinema.payment.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.payment.callback.PaymentCallbackHandler;
import com.xinghe.cinema.payment.dto.PayCallbackRequest;
import com.xinghe.cinema.payment.entity.Payment;
import com.xinghe.cinema.payment.entity.Refund;
import com.xinghe.cinema.payment.service.PaymentService;
import com.xinghe.cinema.payment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * payment-service 对外接口。
 * 支付回调是「订单 CAS → 出票 / 退款」的入口（文档 0.2 / 4.3）。
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    /** 渠道支付回调。 */
    @PostMapping("/callback")
    public Result<String> callback(@Valid @RequestBody PayCallbackRequest req) {
        PaymentCallbackHandler.CallbackResult r = paymentService.handleCallback(
                req.getOrderNo(), req.getPayNo(), req.getAmount(), req.getChannel());
        return r.success() ? Result.ok(r.message()) : Result.fail(r.message());
    }

    /** 模拟支付（无真实渠道时使用），走与真实回调完全相同的处理路径。 */
    @PostMapping("/{orderNo}/pay")
    public Result<String> mockPay(@PathVariable String orderNo) {
        PaymentCallbackHandler.CallbackResult r = paymentService.mockPay(orderNo);
        return r.success() ? Result.ok(r.message()) : Result.fail(r.message());
    }

    @GetMapping("/{orderNo}")
    public Result<Payment> detail(@PathVariable String orderNo) {
        return Result.ok(paymentService.getByOrderNo(orderNo));
    }

    @GetMapping("/refund/{orderNo}")
    public Result<List<Refund>> refunds(@PathVariable String orderNo) {
        return Result.ok(refundService.listByOrderNo(orderNo));
    }
}
