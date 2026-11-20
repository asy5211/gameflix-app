package com.gameflix.controller;

import com.gameflix.security.JwtService;
import com.gameflix.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;

/** Subscription page with a single Subscribe / Cancel button. No payments are handled. Login required. */
@Controller
public class SubscriptionController {

    private static final String JWT_KEY = "jwt";

    private final SubscriptionService subscriptionService;
    private final JwtService jwtService;

    public SubscriptionController(SubscriptionService subscriptionService, JwtService jwtService) {
        this.subscriptionService = subscriptionService;
        this.jwtService = jwtService;
    }

    @GetMapping("/subscription")
    public String page(HttpSession session, Model model) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("subscribed", subscriptionService.isSubscribed(userId.get()));
        return "subscription";
    }

    @PostMapping("/subscription/toggle")
    public String toggle(HttpSession session) {
        Optional<Long> userId = userId(session);
        if (userId.isEmpty()) {
            return "redirect:/login";
        }
        subscriptionService.toggle(userId.get());
        return "redirect:/subscription";
    }

    private Optional<Long> userId(HttpSession session) {
        Object token = session.getAttribute(JWT_KEY);
        if (!(token instanceof String jwt)) {
            return Optional.empty();
        }
        return jwtService.validateAndGetUserId(jwt);
    }
}
