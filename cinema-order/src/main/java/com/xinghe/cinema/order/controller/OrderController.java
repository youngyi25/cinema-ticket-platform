package com.xinghe.cinema.order.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.order.dto.ChangeRequest;
import com.xinghe.cinema.order.dto.ChangeResult;
import com.xinghe.cinema.order.dto.LockOrderRequest;
import com.xinghe.cinema.order.dto.OrderVO;
import com.xinghe.cinema.order.entity.ChangeRecord;
import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.RefundRecord;
import com.xinghe.cinema.order.service.OrderChangeService;
import com.xinghe.cinema.order.service.OrderRefundService;
import com.xinghe.cinema.order.service.OrderService;
import com.xinghe.cinema.order.service.ReconcileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * order-service 对外接口。
 * 主链路：POST /order/lock → POST /payment/callback（见 payment 模块）。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final OrderRefundService orderRefundService;
    private final OrderChangeService orderChangeService;
    private final ReconcileService reconcileService;

    public OrderController(OrderService orderService, OrderRefundService orderRefundService,
                           OrderChangeService orderChangeService, ReconcileService reconcileService) {
        this.orderService = orderService;
        this.orderRefundService = orderRefundService;
        this.orderChangeService = orderChangeService;
        this.reconcileService = reconcileService;
    }

    /** 提交订单：Lua 原子锁座 → 写订单(LOCKED) → 发延迟消息。 */
    @PostMapping("/lock")
    public Result<OrderVO> lock(@Valid @RequestBody LockOrderRequest req) {
        return Result.ok(orderService.lock(req));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @GetMapping("/no/{orderNo}")
    public Result<OrderVO> detailByNo(@PathVariable String orderNo) {
        return Result.ok(orderService.detailByOrderNo(orderNo));
    }

    @GetMapping("/list")
    public Result<List<Order>> list(@RequestParam Long memberId) {
        return Result.ok(orderService.listByMember(memberId));
    }

    /** 用户取消（仅待支付）。 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) Long memberId) {
        orderService.cancel(id, memberId);
        return Result.ok();
    }

    /** 申请退票：按阶梯费率扣手续费，释放座位。 */
    @PostMapping("/{id}/refund")
    public Result<RefundRecord> refund(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        return Result.ok(orderRefundService.apply(id, reason));
    }

    @GetMapping("/no/{orderNo}/refunds")
    public Result<List<RefundRecord>> refunds(@PathVariable String orderNo) {
        return Result.ok(orderRefundService.listByOrderNo(orderNo));
    }

    /** 改签（先占后放）。 */
    @PostMapping("/change")
    public Result<ChangeResult> change(@Valid @RequestBody ChangeRequest req) {
        return Result.ok(orderChangeService.change(req));
    }

    /** 改签补款确认（mock 支付成功后调用）。 */
    @PostMapping("/change/{changeId}/pay")
    public Result<ChangeResult> confirmChangePay(@PathVariable Long changeId) {
        return Result.ok(orderChangeService.confirmPay(changeId));
    }

    @GetMapping("/{id}/changes")
    public Result<List<ChangeRecord>> changes(@PathVariable Long id) {
        return Result.ok(orderChangeService.listByOrder(id));
    }

    /** 对账检查（文档 8.2 的三条 SQL）。 */
    @GetMapping("/admin/reconcile")
    public Result<Map<String, Object>> reconcile() {
        return Result.ok(reconcileService.check());
    }

    /** 手动补清改签座位泄漏。 */
    @PostMapping("/admin/change/repair")
    public Result<Integer> repair() {
        return Result.ok(orderChangeService.repairLeakedOldSeats());
    }
}
