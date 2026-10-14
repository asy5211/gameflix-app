package com.gameflix;

import com.jayway.jsonpath.JsonPath;
import com.gameflix.repository.UserRepository;
import com.gameflix.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthFlowTest {

    // Random per-run key: no secret is ever committed.
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        r.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(k));
    }

    // MockMvc is built by hand (with the Spring Security filter chain applied) so the test does not depend on
    // the @AutoConfigureMockMvc package, which moved in Spring Boot 4.
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Autowired UserRepository users;
    @Autowired JwtService jwtService;

    private static final String PASSWORD = "Throwaway#Pass1";

    private String uniqueEmail() {
        return "player-" + UUID.randomUUID() + "@example.com";
    }

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Player One\"}";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String loginToken(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.token");
    }

    // 1. successful registration (+ hash never returned, stored value is a hash)
    @Test
    void registerSucceeds_andStoresHashNotPassword() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String stored = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals(PASSWORD, stored);
        assertTrue(stored.startsWith("{bcrypt}$2"), "expected a delegating-encoder bcrypt hash but got prefix");
    }

    // 2. duplicate email rejected (409, driven by the DB unique constraint)
    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isConflict());
        // same address, different case, is the same account
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputIs400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueEmail(), "x".repeat(65))))
                .andExpect(status().isBadRequest());
    }

    // 3. successful login returns a token
    @Test
    void loginSucceedsAndReturnsToken() throws Exception {
        String email = uniqueEmail();
        register(email);
        assertFalse(loginToken(email).isBlank());
    }

    // 4. wrong password -> 401, and unknown email gives the identical response
    @Test
    void wrongPasswordIs401_withSameMessageAsUnknownEmail() throws Exception {
        String email = uniqueEmail();
        register(email);

        String wrongPw = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPassword!9\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPw, unknown, "failure body must not reveal whether the email exists");
    }

    // 5. protected call with a valid token
    @Test
    void meWithValidTokenReturnsOwnProfile() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = loginToken(email);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // 6a. missing token
    @Test
    void meWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    // 6b. tampered token
    @Test
    void meWithTamperedTokenIs401() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = loginToken(email);
        String[] parts = token.split("\\.");
        char c = parts[2].charAt(5);
        String badSig = parts[2].substring(0, 5) + (c == 'A' ? 'B' : 'A') + parts[2].substring(6);
        String tampered = parts[0] + "." + parts[1] + "." + badSig;
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    // extra: expired token
    @Test
    void meWithExpiredTokenIs401() throws Exception {
        String email = uniqueEmail();
        register(email);
        Long id = users.findByEmail(email).orElseThrow().getId();
        String expired = jwtService.issueToken(id, Duration.ofSeconds(-60));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }
}
