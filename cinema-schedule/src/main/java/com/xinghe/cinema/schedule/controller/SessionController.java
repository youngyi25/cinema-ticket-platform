package com.xinghe.cinema.schedule.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.schedule.dto.BatchScheduleRequest;
import com.xinghe.cinema.schedule.dto.CreateSessionRequest;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * schedule-service 对外接口。
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    public Result<Session> detail(@PathVariable Long id) {
        return Result.ok(sessionService.getRequired(id));
    }

    @GetMapping("/list")
    public Result<List<Session>> list(
            @RequestParam Long cinemaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(sessionService.listByCinema(cinemaId, date));
    }

    @GetMapping("/by-movie")
    public Result<List<Session>> byMovie(
            @RequestParam Long movieId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(sessionService.listByMovie(movieId, date));
    }

    @PostMapping
    public Result<Session> create(@Valid @RequestBody CreateSessionRequest req) {
        return Result.ok(sessionService.create(req));
    }

    /** 批量排片：全成功或全失败。 */
    @PostMapping("/batch")
    public Result<List<Session>> batch(@Valid @RequestBody BatchScheduleRequest req) {
        return Result.ok(sessionService.createBatch(req));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sessionService.updateStatus(id, status);
        return Result.ok();
    }
}
