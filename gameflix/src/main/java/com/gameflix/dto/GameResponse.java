package com.gameflix.dto;

import com.gameflix.model.Game;

public record GameResponse(Long id, String title, String description, String genre, Integer releaseYear) {

    public static GameResponse from(Game game) {
        return new GameResponse(game.getId(), game.getTitle(), game.getDescription(),
                game.getGenre().getName(), game.getReleaseYear());
    }
}
