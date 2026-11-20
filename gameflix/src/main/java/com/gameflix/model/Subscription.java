package com.gameflix.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A user's subscription. No payment is handled: subscribing and canceling just switch access on and off. */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Subscription() {
    }

    public Subscription(User user) {
        this.user = user;
        this.status = SubscriptionStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        this.startedAt = Instant.now();
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.endedAt = Instant.now();
    }

    public Long getId() { return id; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
