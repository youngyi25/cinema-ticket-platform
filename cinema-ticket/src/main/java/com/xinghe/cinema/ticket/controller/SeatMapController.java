package com.xinghe.cinema.ticket.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.ticket.dto.SeatMapVO;
import com.xinghe.cinema.ticket.entity.Ticket;
import com.xinghe.cinema.ticket.service.BitmapRebuildService;
import com.xinghe.cinema.ticket.service.SeatMapService;
import com.xinghe.cinema.ticket.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ticket-service 对外接口。
 */
@RestController
public class SeatMapController {

    private final SeatMapService seatMapService;
    private final TicketService ticketService;
    private final BitmapRebuildService bitmapRebuildService;

    public SeatMapController(SeatMapService seatMapService, TicketService ticketService,
                             BitmapRebuildService bitmapRebuildService) {
        this.seatMapService = seatMapService;
        this.ticketService = ticketService;
        this.bitmapRebuildService = bitmapRebuildService;
    }

    /** 选座页：座位矩阵 + 三态 + 分区价格 + 连座推荐（文档 0.2 / 3.5）。 */
    @GetMapping("/session/{id}/seatmap")
    public Result<SeatMapVO> seatMap(@PathVariable Long id,
                                     @RequestParam(required = false, defaultValue = "2") Integer need) {
        return Result.ok(seatMapService.seatMap(id, need));
    }

    @GetMapping("/ticket/order/{orderId}")
    public Result<List<Ticket>> byOrder(@PathVariable Long orderId) {
        return Result.ok(ticketService.listByOrder(orderId));
    }

    /** 运维接口：Redis 数据丢失后重建 bitmap（文档 4.5）。 */
    @PostMapping("/admin/bitmap/rebuild")
    public Result<BitmapRebuildService.RebuildResult> rebuild() {
        return Result.ok(bitmapRebuildService.rebuildAll());
    }
}
