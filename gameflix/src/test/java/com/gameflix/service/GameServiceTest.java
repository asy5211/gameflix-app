package com.gameflix.service;

import com.gameflix.model.Favorite;
import com.gameflix.model.Game;
import com.gameflix.model.Genre;
import com.gameflix.model.User;
import com.gameflix.repository.FavoriteRepository;
import com.gameflix.repository.GameRepository;
import com.gameflix.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock GameRepository games;
    @Mock FavoriteRepository favorites;
    @Mock UserRepository users;
    @InjectMocks GameService service;

    private Game game() {
        return new Game("Mind Maze", "Mazes and puzzles.", 2019, new Genre("Puzzle"));
    }

    @Test
    void blankSearchReturnsTheWholeCatalog() {
        List<Game> all = List.of(game());
        when(games.findAllByOrderByTitleAsc()).thenReturn(all);

        assertEquals(all, service.search("   "));
        assertEquals(all, service.search(null));
        verify(games, never()).findByTitleContainingIgnoreCaseOrderByTitleAsc(any());
    }

    @Test
    void searchTrimsTheQueryBeforeMatching() {
        List<Game> match = List.of(game());
        when(games.findByTitleContainingIgnoreCaseOrderByTitleAsc("maze")).thenReturn(match);

        assertEquals(match, service.search("  maze  "));
    }

    @Test
    void addFavoriteSavesWhenGameExistsAndIsNotYetFavorite() {
        when(games.findById(1L)).thenReturn(Optional.of(game()));
        when(favorites.existsByUser_IdAndGame_Id(5L, 1L)).thenReturn(false);
        when(users.getReferenceById(5L)).thenReturn(new User("a@example.com", "hash", "Fan"));

        assertTrue(service.addFavorite(5L, 1L));
        verify(favorites).saveAndFlush(any(Favorite.class));
    }

    @Test
    void addFavoriteTwiceDoesNotSaveAgain() {
        when(games.findById(1L)).thenReturn(Optional.of(game()));
        when(favorites.existsByUser_IdAndGame_Id(5L, 1L)).thenReturn(true);

        assertTrue(service.addFavorite(5L, 1L));
        verify(favorites, never()).saveAndFlush(any(Favorite.class));
    }

    @Test
    void addFavoriteForUnknownGameReturnsFalseAndSavesNothing() {
        when(games.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.addFavorite(5L, 99L));
        verify(favorites, never()).saveAndFlush(any(Favorite.class));
    }
}
