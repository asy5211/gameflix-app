package com.gameflix.controller;

import com.gameflix.model.Game;
import com.gameflix.security.JwtService;
import com.gameflix.service.GameService;
import com.gameflix.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Thymeleaf pages for the game catalog: list + search, detail page, favorites, play. Login required. */
@Controller
public class GameController {

    private static final String JWT_KEY = "jwt";

    private final GameService gameService;
    private final SubscriptionService subscriptionService;
    private final JwtService jwtService;

    public GameController(GameService gameService, SubscriptionService subscriptionService, JwtService jwtService) {
        this.gameService = gameService;
        this.subscriptionService = subscriptionService;
        this.jwtService = jwtService;
    }

    @GetMapping("/games")
    public String catalog(@RequestParam(defaultValue = "") String q, HttpSession session, Model model) {
        if (userId(session).isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("games", gameService.search(q));
        model.addAttribute("q", q);
        return "games";
    }

    @GetMapping("/games/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        Game game = gameService.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("game", game);
        model.addAttribute("favorite", gameService.isFavorite(userId.get(), id));
        model.addAttribute("canPlay", subscriptionService.isSubscribed(userId.get()));
        return "game";
    }

    /** Simulated game screen. Access is enforced here on the server, not just by hiding the button. */
    @GetMapping("/games/{id}/play")
    public String play(@PathVariable Long id, HttpSession session, Model model) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        Game game = gameService.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!subscriptionService.isSubscribed(userId.get())) {
            return "redirect:/subscription";
        }
        model.addAttribute("game", game);
        return "play";
    }

    @PostMapping("/games/{id}/favorite")
    public String favorite(@PathVariable Long id, HttpSession session) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        if (!gameService.addFavorite(userId.get(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/games/" + id;
    }

    @PostMapping("/games/{id}/unfavorite")
    public String unfavorite(@PathVariable Long id, HttpSession session) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        gameService.removeFavorite(userId.get(), id);
        return "redirect:/games/" + id;
    }

    private Optional<Long> userId(HttpSession session) {
        Object token = session.getAttribute(JWT_KEY);
        if (!(token instanceof String jwt)) {
            return Optional.empty();
        }
        return jwtService.validateAndGetUserId(jwt);
    }
}
