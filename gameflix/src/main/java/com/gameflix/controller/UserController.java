package com.gameflix.controller;

import com.gameflix.dto.UserResponse;
import com.gameflix.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    /** The caller is identified only from the verified token's subject claim - never from a request parameter. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return auth.profile(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build()); // token valid but user deleted
    }
}
