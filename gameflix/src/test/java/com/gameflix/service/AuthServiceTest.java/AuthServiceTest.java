package com.gameflix.service;

import com.gameflix.dto.LoginRequest;
import com.gameflix.dto.RegisterRequest;
import com.gameflix.dto.TokenResponse;
import com.gameflix.dto.UserResponse;
import com.gameflix.exception.EmailAlreadyRegisteredException;
import com.gameflix.exception.InvalidCredentialsException;
import com.gameflix.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthService (GameFlix's service layer), written with JUnit 5 for Spring Boot 4.
 * They call the service directly (no HTTP) and use the separate gameflix_test database, so they run
 * outside the Docker container and never touch real data.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    // Random per-run signing key: no secret is ever committed.
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        r.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(k));
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    private static final String PASSWORD = "Throwaway#Pass1";

    private static String uniqueEmail() {
        return "player-" + UUID.randomUUID() + "@example.com";
    }

    private UserResponse registerUser(String email) {
        return authService.register(new RegisterRequest(email, PASSWORD, "Player One"));
    }

    // Example test (same idea as the assignment's getAllMovies check):
    // a newly registered user is returned with an id, and the service can look the same profile up again.
    @Test
    void register_ShouldReturnProfileThatCanBeLookedUp() {
        String email = uniqueEmail();

        UserResponse created = registerUser(email);

        assertNotNull(created.id());
        assertNotNull(created.createdAt());
        Optional<UserResponse> found = authService.profile(created.id());
        assertTrue(found.isPresent());
        assertEquals(email, found.get().email());
    }

    // Additional test 1: real service logic - the email is trimmed and lower-cased, the display name is trimmed.
    @Test
    void register_ShouldNormalizeEmailAndTrimDisplayName() {
        String local = "player-" + UUID.randomUUID();
        String messy = "  " + local.toUpperCase() + "@Example.COM  ";

        UserResponse created = authService.register(new RegisterRequest(messy, PASSWORD, "  Player One  "));

        assertEquals(local.toLowerCase() + "@example.com", created.email());
        assertEquals("Player One", created.displayName());
    }

    // Additional test 2: real service logic - a second account with the same email is rejected,
    // even when the address is written in a different case.
    @Test
    void register_ShouldRejectDuplicateEmailRegardlessOfCase() {
        String email = uniqueEmail();
        registerUser(email);

        assertThrows(EmailAlreadyRegisteredException.class, () -> registerUser(email));
        assertThrows(EmailAlreadyRegisteredException.class, () -> registerUser(email.toUpperCase()));
    }

    // Additional test 3: login returns a Bearer token that really identifies the registered user.
    @Test
    void login_ShouldReturnTokenForThatUser_EvenWithMessyEmailCase() {
        String email = uniqueEmail();
        UserResponse created = registerUser(email);

        TokenResponse token = authService.login(new LoginRequest("  " + email.toUpperCase() + " ", PASSWORD));

        assertEquals("Bearer", token.tokenType());
        assertEquals(jwtService.getLifetime().toSeconds(), token.expiresInSeconds());
        assertEquals(Optional.of(created.id()), jwtService.validateAndGetUserId(token.token()));
    }

    // Additional test 4: a wrong password and an unknown email fail with the identical generic message,
    // so the response never reveals which emails are registered.
    @Test
    void login_ShouldFailTheSameWayForWrongPasswordAndUnknownEmail() {
        String email = uniqueEmail();
        registerUser(email);

        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest(email, "WrongPassword!9")));
        InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest(uniqueEmail(), PASSWORD)));

        assertEquals(InvalidCredentialsException.MESSAGE, wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }

    // Additional test 5: missing or blank credentials are rejected before any lookup.
    @Test
    void login_ShouldRejectNullOrBlankCredentials() {
        assertThrows(InvalidCredentialsException.class, () -> authService.login(null));
        assertThrows(InvalidCredentialsException.class, () -> authService.login(new LoginRequest(null, PASSWORD)));
        assertThrows(InvalidCredentialsException.class, () -> authService.login(new LoginRequest("   ", PASSWORD)));
        assertThrows(InvalidCredentialsException.class, () -> authService.login(new LoginRequest(uniqueEmail(), "")));
    }

    // Additional test 6: looking up a user id that does not exist gives an empty result, not an error.
    @Test
    void profile_ShouldBeEmptyForUnknownUserId() {
        assertTrue(authService.profile(-1L).isEmpty());
    }
}
