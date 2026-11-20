package com.gameflix.service;

import com.gameflix.model.Favorite;
import com.gameflix.model.Game;
import com.gameflix.repository.FavoriteRepository;
import com.gameflix.repository.GameRepository;
import com.gameflix.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GameService {

    private final GameRepository games;
    private final FavoriteRepository favorites;
    private final UserRepository users;

    public GameService(GameRepository games, FavoriteRepository favorites, UserRepository users) {
        this.games = games;
        this.favorites = favorites;
        this.users = users;
    }

    /** Blank or missing query returns the whole catalog; otherwise a case-insensitive title match. */
    @Transactional(readOnly = true)
    public List<Game> search(String query) {
        if (query == null || query.isBlank()) {
            return games.findAllByOrderByTitleAsc();
        }
        return games.findByTitleContainingIgnoreCaseOrderByTitleAsc(query.trim());
    }

    @Transactional(readOnly = true)
    public Optional<Game> find(Long id) {
        return games.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long gameId) {
        return favorites.existsByUser_IdAndGame_Id(userId, gameId);
    }

    @Transactional(readOnly = true)
    public List<Game> favoritesOf(Long userId) {
        return favorites.findGamesByUserId(userId);
    }

    /** Idempotent: favoriting an already-favorite game changes nothing. Returns false if the game does not exist. */
    @Transactional
    public boolean addFavorite(Long userId, Long gameId) {
        Optional<Game> game = games.findById(gameId);
        if (game.isEmpty()) {
            return false;
        }
        if (!favorites.existsByUser_IdAndGame_Id(userId, gameId)) {
            favorites.saveAndFlush(new Favorite(users.getReferenceById(userId), game.get()));
        }
        return true;
    }

    @Transactional
    public void removeFavorite(Long userId, Long gameId) {
        favorites.deleteByUser_IdAndGame_Id(userId, gameId);
    }
}
