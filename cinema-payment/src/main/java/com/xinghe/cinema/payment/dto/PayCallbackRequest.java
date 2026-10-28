package com.xinghe.cinema.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付回调报文。真实渠道还会带签名，这里保留 sign 字段用于验签扩展。
 */
@Data
public class PayCallbackRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "支付流水号不能为空")
    private String payNo;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.00", message = "金额不能为负")
    private BigDecimal amount;

    private String channel = "MOCK";

    private String sign;
}
