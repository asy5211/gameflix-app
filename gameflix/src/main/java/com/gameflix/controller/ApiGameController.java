package com.gameflix.controller;

import com.gameflix.dto.GameResponse;
import com.gameflix.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** JSON catalog endpoints. Protected: SecurityConfig requires a valid token for everything under /api except /api/auth. */
@RestController
@RequestMapping("/api/games")
public class ApiGameController {

    private final GameService gameService;

    public ApiGameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<GameResponse> list() {
        return gameService.search(null).stream().map(GameResponse::from).toList();
    }

    @GetMapping("/search")
    public List<GameResponse> search(@RequestParam(defaultValue = "") String q) {
        return gameService.search(q).stream().map(GameResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameResponse> one(@PathVariable Long id) {
        return gameService.find(id)
                .map(GameResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
