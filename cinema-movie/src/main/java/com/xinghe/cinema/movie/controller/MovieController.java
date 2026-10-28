package com.xinghe.cinema.movie.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.movie.entity.Movie;
import com.xinghe.cinema.movie.service.MovieService;
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
 * movie-service 对外接口。
 */
@RestController
@RequestMapping("/movie")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/list")
    public Result<List<Movie>> list(@RequestParam(defaultValue = "true") boolean onShelfOnly) {
        return Result.ok(onShelfOnly ? movieService.listOnShelf() : movieService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        return Result.ok(movieService.getRequired(id));
    }

    @PostMapping
    public Result<Movie> create(@RequestBody Movie movie) {
        return Result.ok(movieService.create(movie));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        movieService.updateStatus(id, status);
        return Result.ok();
    }
}
