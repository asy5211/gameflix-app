package com.gameflix.controller;

import com.gameflix.dto.LoginRequest;
import com.gameflix.dto.RegisterRequest;
import com.gameflix.dto.TokenResponse;
import com.gameflix.dto.UserResponse;
import com.gameflix.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest req) {
        return auth.login(req);
    }
}
