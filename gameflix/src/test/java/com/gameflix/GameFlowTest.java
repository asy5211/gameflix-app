package com.gameflix;

import com.gameflix.repository.GameRepository;
import com.gameflix.repository.UserRepository;
import com.gameflix.security.JwtService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Game catalog, search, detail page and favorites: web pages and the protected JSON API. */
@SpringBootTest
@ActiveProfiles("test")
class GameFlowTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        r.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(k));
    }

    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    @Autowired UserRepository users;
    @Autowired JwtService jwtService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String PASSWORD = "Throwaway#Pass1";

    private String registerNewUser() throws Exception {
        String email = "games-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("password", PASSWORD).param("displayName", "Game Fan"))
                .andExpect(status().is3xxRedirection());
        return email;
    }

    private MockHttpSession loginSession(String email) throws Exception {
        MvcResult res = mvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private MockHttpSession newSession() throws Exception {
        return loginSession(registerNewUser());
    }

    private Long mindMazeId() {
        return games.findByTitleContainingIgnoreCaseOrderByTitleAsc("Mind Maze").get(0).getId();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void catalogRequiresLogin() throws Exception {
        mvc.perform(get("/games")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void catalogListsTheStarterGames() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mind Maze")))
                .andExpect(content().string(containsString("Neon Drift")));
    }

    @Test
    void searchFiltersByTitleIgnoringCase() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games").param("q", "mAzE").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mind Maze")))
                .andExpect(content().string(not(containsString("Neon Drift"))));
    }

    @Test
    void searchWithNoMatchShowsAMessage() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games").param("q", "zzzz-no-such-game").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No games match")));
    }

    @Test
    void detailPageShowsTheGame() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games/" + mindMazeId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mind Maze")))
                .andExpect(content().string(containsString("Puzzle")))
                .andExpect(content().string(containsString("Add to favorites")));
    }

    @Test
    void unknownGameIs404() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(get("/games/999999999").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void favoriteShowsOnProfileAndCanBeRemoved() throws Exception {
        MockHttpSession session = newSession();
        Long id = mindMazeId();

        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("no favorites yet")));

        mvc.perform(post("/games/" + id + "/favorite").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/games/" + id));

        mvc.perform(get("/games/" + id).session(session))
                .andExpect(content().string(containsString("Remove from favorites")));
        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("Mind Maze")));

        mvc.perform(post("/games/" + id + "/unfavorite").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(not(containsString("Mind Maze"))));
    }

    @Test
    void favoritingTwiceDoesNotDuplicate() throws Exception {
        MockHttpSession session = newSession();
        Long id = mindMazeId();
        mvc.perform(post("/games/" + id + "/favorite").session(session).with(csrf()));
        mvc.perform(post("/games/" + id + "/favorite").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        String profile = mvc.perform(get("/profile").session(session))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, occurrences(profile, "Mind Maze"));
    }

    @Test
    void favoritesBelongToTheUserWhoSavedThem() throws Exception {
        MockHttpSession first = newSession();
        MockHttpSession second = newSession();
        mvc.perform(post("/games/" + mindMazeId() + "/favorite").session(first).with(csrf()));

        mvc.perform(get("/profile").session(second))
                .andExpect(content().string(not(containsString("Mind Maze"))));
    }

    @Test
    void favoritePostWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = newSession();
        mvc.perform(post("/games/" + mindMazeId() + "/favorite").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiGamesRequireATokenAndSearchWorks() throws Exception {
        String email = registerNewUser();
        String token = jwtService.issueToken(users.findByEmail(email).orElseThrow().getId());

        mvc.perform(get("/api/games")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/games").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Neon Drift")));

        mvc.perform(get("/api/games/search").param("q", "maze").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mind Maze")))
                .andExpect(content().string(not(containsString("Neon Drift"))));

        mvc.perform(get("/api/games/" + mindMazeId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Puzzle")));

        mvc.perform(get("/api/games/999999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
