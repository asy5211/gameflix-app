package com.gameflix.repository;

import com.gameflix.model.Subscription;
import com.gameflix.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByUser_IdAndStatusOrderByIdDesc(Long userId, SubscriptionStatus status);
}
