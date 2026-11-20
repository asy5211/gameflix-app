package com.gameflix.config;

import com.gameflix.model.Game;
import com.gameflix.model.Genre;
import com.gameflix.repository.GameRepository;
import com.gameflix.repository.GenreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Loads a small starter catalog the first time the app runs against an empty games table. */
@Component
public class GameDataSeeder implements CommandLineRunner {

    private final GenreRepository genres;
    private final GameRepository games;

    public GameDataSeeder(GenreRepository genres, GameRepository games) {
        this.genres = genres;
        this.games = games;
    }

    @Override
    public void run(String... args) {
        if (games.count() > 0) {
            return;
        }
        add("Crimson Blade", "Action", 2022, "A fast sword-fighting adventure through a burning kingdom. Learn combos, dodge traps and face the Ember King.");
        add("Shadow Run", "Action", 2021, "Sneak, sprint and leap across rooftops in a night-time heist thriller where every second counts.");
        add("Dungeon Keep", "RPG", 2020, "Build a party, explore a living dungeon and decide who rules the keep. Over forty hours of quests.");
        add("Realm of Echoes", "RPG", 2023, "A story-driven role-playing game where your choices echo through a world of floating islands.");
        add("Mind Maze", "Puzzle", 2019, "Hundreds of handcrafted mazes and logic puzzles that get trickier with every level.");
        add("Block Bloom", "Puzzle", 2022, "A relaxing match-and-grow puzzle game. Clear blocks to make a garden bloom.");
        add("Star Forge", "Strategy", 2021, "Command a fleet, mine asteroids and outsmart rival empires in real-time space strategy.");
        add("Fortress Wars", "Strategy", 2018, "Design your fortress, train your army and defend it against waves of siege engines.");
        add("Pixel Striker", "Sports", 2020, "Arcade football with big heads, bigger shots and a seven-day tournament mode.");
        add("Skyline Soccer", "Sports", 2023, "Five-a-side soccer on rooftop pitches above a neon city. Quick matches, online-style leagues.");
        add("Neon Drift", "Racing", 2022, "Drift through glowing city streets in a high-speed racer with upgradeable cars.");
        add("Turbo Kart Arena", "Racing", 2019, "Chaotic kart racing with power-ups, shortcuts and a four-player party mode.");
    }

    private void add(String title, String genreName, int year, String description) {
        Genre genre = genres.findByName(genreName).orElseGet(() -> genres.save(new Genre(genreName)));
        games.save(new Game(title, description, year, genre));
    }
}
