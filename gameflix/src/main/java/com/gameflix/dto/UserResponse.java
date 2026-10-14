package com.gameflix.dto;

import com.gameflix.model.User;

import java.time.Instant;

/** Public profile. Deliberately has no password / hash field. */
public record UserResponse(Long id, String email, String displayName, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getCreatedAt());
    }
}
