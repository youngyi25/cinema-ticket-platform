package com.xinghe.cinema.order.dto;

import com.xinghe.cinema.order.entity.Order;
import com.xinghe.cinema.order.entity.OrderItem;
import com.xinghe.cinema.ticket.entity.Ticket;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情返回体。
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Integer status;
    private String statusDesc;

    private Long memberId;
    private Long sessionId;
    private String seatLabels;
    private String seatIds;

    private BigDecimal totalAmount;
    private BigDecimal payAmount;

    private LocalDateTime lockExpireAt;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    /** 场次信息，便于前端直接展示。 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String movieName;
    private String hallName;
    private String cinemaName;

    private List<OrderItem> items;
    private List<Ticket> tickets;

    public static OrderVO of(Order o) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setStatus(o.getStatus());
        vo.setMemberId(o.getMemberId());
        vo.setSessionId(o.getSessionId());
        vo.setSeatLabels(o.getSeatLabels());
        vo.setSeatIds(o.getSeatIds());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setPayAmount(o.getPayAmount());
        vo.setLockExpireAt(o.getLockExpireAt());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setPaidAt(o.getPaidAt());
        return vo;
    }
}
