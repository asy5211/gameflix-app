package com.gameflix.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A user's favorite game. Resolves the many-to-many between users and games (the Module 3 "user library"). */
@Entity
@Table(name = "favorites",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorites_user_game", columnNames = {"user_id", "game_id"}))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    public Favorite(User user, Game game) {
        this.user = user;
        this.game = game;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public Instant getCreatedAt() { return createdAt; }
}
