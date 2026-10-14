package com.gameflix.dto;

public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
}
