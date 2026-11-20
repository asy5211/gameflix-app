package com.gameflix.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "release_year", nullable = false)
    private Integer releaseYear;

    // One genre has many games (Module 3 ERD: Games.genre_id -> Genres.id).
    @ManyToOne(optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Game() {
    }

    public Game(String title, String description, Integer releaseYear, Genre genre) {
        this.title = title;
        this.description = description;
        this.releaseYear = releaseYear;
        this.genre = genre;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getReleaseYear() { return releaseYear; }
    public Genre getGenre() { return genre; }
    public Instant getCreatedAt() { return createdAt; }
}
