package com.gameflix;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Smoke tests for the basic Thymeleaf pages (register -> login -> profile -> logout). */
@SpringBootTest
@ActiveProfiles("test")
class PageFlowTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        r.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(k));
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String PASSWORD = "Throwaway#Pass1";

    @Test
    void homePageLoads() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("GameFlix")));
    }

    @Test
    void profileRedirectsToLoginWhenNotLoggedIn() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void registerShowsValidationErrors() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("email", "not-an-email").param("password", "short").param("displayName", "X"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<li")));
    }

    @Test
    void registerRejectsAPasswordWithoutASymbol() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("email", "weak-" + UUID.randomUUID() + "@example.com")
                        .param("password", "Throwaway1Pass").param("displayName", "Weak Pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("password must contain a symbol")));
    }

    @Test
    void loginWithWrongPasswordShowsGenericError() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("email", "nobody-" + UUID.randomUUID() + "@example.com").param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password")));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/login").param("email", "a@example.com").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerLoginProfileLogoutFlow() throws Exception {
        String email = "page-" + UUID.randomUUID() + "@example.com";

        mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("password", PASSWORD).param("displayName", "Page Player"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        MvcResult login = mvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page Player")))
                .andExpect(content().string(containsString(email)));

        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/profile").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
