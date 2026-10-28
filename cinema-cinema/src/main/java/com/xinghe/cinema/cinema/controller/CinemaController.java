package com.xinghe.cinema.cinema.controller;

import com.xinghe.cinema.cinema.dto.HallCreateRequest;
import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.entity.Seat;
import com.xinghe.cinema.cinema.service.CinemaService;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * cinema-service 对外接口。
 */
@RestController
@RequestMapping("/cinema")
public class CinemaController {

    private final CinemaService cinemaService;
    private final HallService hallService;

    public CinemaController(CinemaService cinemaService, HallService hallService) {
        this.cinemaService = cinemaService;
        this.hallService = hallService;
    }

    @GetMapping("/list")
    public Result<List<Cinema>> list() {
        return Result.ok(cinemaService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Cinema> detail(@PathVariable Long id) {
        return Result.ok(cinemaService.getRequired(id));
    }

    @PostMapping
    public Result<Cinema> create(@RequestBody Cinema cinema) {
        return Result.ok(cinemaService.create(cinema));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        cinemaService.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/{cinemaId}/halls")
    public Result<List<Hall>> halls(@PathVariable Long cinemaId) {
        return Result.ok(cinemaService.listHalls(cinemaId));
    }

    /** 建厅，同时生成 row_count × col_count 个座位。 */
    @PostMapping("/hall")
    public Result<Hall> createHall(@Valid @RequestBody HallCreateRequest req) {
        return Result.ok(hallService.createHall(req));
    }

    @GetMapping("/hall/{hallId}")
    public Result<Hall> hall(@PathVariable Long hallId) {
        return Result.ok(hallService.getRequired(hallId));
    }

    @GetMapping("/hall/{hallId}/seats")
    public Result<List<Seat>> seats(@PathVariable Long hallId) {
        return Result.ok(hallService.listSeats(hallId));
    }
}
