package com.gameflix.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        // bcrypt silently truncates input at 72 bytes, so cap at 64 characters.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password must be 8 to 64 characters")
        @Pattern(regexp = ".*[a-z].*", message = "password must contain a lowercase letter")
        @Pattern(regexp = ".*[A-Z].*", message = "password must contain an uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "password must contain a number")
        @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "password must contain a symbol")
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
