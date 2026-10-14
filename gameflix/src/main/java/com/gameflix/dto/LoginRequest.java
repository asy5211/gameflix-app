package com.gameflix.dto;

public record LoginRequest(String email, String password) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
