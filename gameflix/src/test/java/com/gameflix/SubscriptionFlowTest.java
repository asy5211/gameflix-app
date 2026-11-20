package com.gameflix;

import com.gameflix.repository.GameRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The subscription toggle gates playing a game. No payments are involved. */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionFlowTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        r.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(k));
    }

    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String PASSWORD = "Throwaway#Pass1";

    private MockHttpSession newSession() throws Exception {
        String email = "sub-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("password", PASSWORD).param("displayName", "Subscriber"))
                .andExpect(status().is3xxRedirection());
        MvcResult res = mvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private Long gameId() {
        return games.findByTitleContainingIgnoreCaseOrderByTitleAsc("Mind Maze").get(0).getId();
    }

    private void toggle(MockHttpSession session) throws Exception {
        mvc.perform(post("/subscription/toggle").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subscription"));
    }

    @Test
    void subscriptionPageRequiresLogin() throws Exception {
        mvc.perform(get("/subscription")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void newUserIsNotSubscribedAndSeesALockedGame() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/subscription").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No active subscription")));
        mvc.perform(get("/games/" + gameId()).session(session))
                .andExpect(content().string(containsString("Subscribe to play")))
                .andExpect(content().string(not(containsString("Play now"))));
    }

    @Test
    void playIsBlockedWithoutASubscription() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games/" + gameId() + "/play").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subscription"));
    }

    @Test
    void subscribingUnlocksPlaying() throws Exception {
        MockHttpSession session = newSession();
        toggle(session);

        mvc.perform(get("/subscription").session(session))
                .andExpect(content().string(containsString("Subscription active")));
        mvc.perform(get("/games/" + gameId()).session(session))
                .andExpect(content().string(containsString("Play now")));
        mvc.perform(get("/games/" + gameId() + "/play").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Now playing: Mind Maze")));
    }

    @Test
    void cancelingBlocksPlayingAgain() throws Exception {
        MockHttpSession session = newSession();
        toggle(session);
        toggle(session);

        mvc.perform(get("/subscription").session(session))
                .andExpect(content().string(containsString("No active subscription")));
        mvc.perform(get("/games/" + gameId() + "/play").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subscription"));
    }

    @Test
    void profileShowsTheSubscriptionStatus() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("No active subscription")));
        toggle(session);
        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("Subscription active")));
    }

    @Test
    void subscriptionsAreSeparatePerUser() throws Exception {
        MockHttpSession subscriber = newSession();
        MockHttpSession other = newSession();
        toggle(subscriber);

        mvc.perform(get("/games/" + gameId() + "/play").session(other))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subscription"));
    }

    @Test
    void playingAnUnknownGameIs404() throws Exception {
        MockHttpSession session = newSession();
        toggle(session);
        mvc.perform(get("/games/999999999/play").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void togglePostWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(post("/subscription/toggle").session(session)).andExpect(status().isForbidden());
    }
}
