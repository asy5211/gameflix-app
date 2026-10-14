package com.gameflix.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        // bcrypt silently truncates input at 72 bytes, so cap at 64 characters.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password must be 8 to 64 characters")
        String password,

        @NotBlank(message = "displayName is required")
        @Size(max = 50, message = "displayName must be at most 50 characters")
        String displayName
) {
    // Never print the password, even by accident.
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
