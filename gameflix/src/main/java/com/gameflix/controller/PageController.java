package com.gameflix.controller;

import com.gameflix.dto.LoginRequest;
import com.gameflix.dto.RegisterRequest;
import com.gameflix.dto.TokenResponse;
import com.gameflix.dto.UserResponse;
import com.gameflix.exception.EmailAlreadyRegisteredException;
import com.gameflix.exception.InvalidCredentialsException;
import com.gameflix.security.JwtService;
import com.gameflix.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * Very basic server-rendered (Thymeleaf) front end. It reuses the same AuthService / JwtService as the JSON API:
 * after login the JWT is kept in the server-side HTTP session (never exposed to page JavaScript).
 */
@Controller
public class PageController {

    private static final String JWT_KEY = "jwt";

    private final AuthService authService;
    private final JwtService jwtService;
    private final Validator validator;

    public PageController(AuthService authService, JwtService jwtService, Validator validator) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.validator = validator;
    }

    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        model.addAttribute("loggedIn", currentUser(session).isPresent());
        return "index";
    }

    // ---------- register ----------

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam(defaultValue = "") String email,
                           @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "") String displayName,
                           Model model) {
        RegisterRequest req = new RegisterRequest(email, password, displayName);
        List<String> errors = validator.validate(req).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
        if (errors.isEmpty()) {
            try {
                authService.register(req);
                return "redirect:/login?registered";
            } catch (EmailAlreadyRegisteredException e) {
                errors = List.of(e.getMessage());
            }
        }
        model.addAttribute("errors", errors);
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        return "register";
    }

    // ---------- login (logout is handled by Spring Security: POST /logout) ----------

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String registered, Model model) {
        model.addAttribute("registered", registered != null);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String email,
                        @RequestParam(defaultValue = "") String password,
                        HttpServletRequest request,
                        Model model) {
        try {
            TokenResponse token = authService.login(new LoginRequest(email, password));
            HttpSession old = request.getSession(false);
            if (old != null) {
                old.invalidate(); // new session id after login (prevents session fixation)
            }
            request.getSession(true).setAttribute(JWT_KEY, token.token());
            return "redirect:/profile";
        } catch (InvalidCredentialsException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "login";
        }
    }

    // ---------- profile (needs a valid token in the session) ----------

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Optional<UserResponse> user = currentUser(session);
        if (user.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("user", user.get());
        return "profile";
    }

    // ---------- helpers ----------

    private Optional<UserResponse> currentUser(HttpSession session) {
        Object token = session.getAttribute(JWT_KEY);
        if (!(token instanceof String jwt)) {
            return Optional.empty();
        }
        return jwtService.validateAndGetUserId(jwt).flatMap(authService::profile);
    }
}
