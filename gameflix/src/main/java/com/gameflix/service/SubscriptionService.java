package com.gameflix.service;

import com.gameflix.model.Subscription;
import com.gameflix.model.SubscriptionStatus;
import com.gameflix.repository.SubscriptionRepository;
import com.gameflix.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users) {
        this.subscriptions = subscriptions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> active(Long userId) {
        return subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(userId, SubscriptionStatus.ACTIVE);
    }

    /** Games can be played only while the user has an active subscription. */
    @Transactional(readOnly = true)
    public boolean isSubscribed(Long userId) {
        return active(userId).isPresent();
    }

    /** Returns true when a new subscription was started, false if the user was already subscribed. */
    @Transactional
    public boolean subscribe(Long userId) {
        if (active(userId).isPresent()) {
            return false;
        }
        subscriptions.saveAndFlush(new Subscription(users.getReferenceById(userId)));
        return true;
    }

    /** Returns true when a subscription was canceled, false if there was nothing to cancel. */
    @Transactional
    public boolean cancel(Long userId) {
        Optional<Subscription> current = active(userId);
        current.ifPresent(Subscription::cancel);
        return current.isPresent();
    }

    /** One button: subscribes when not subscribed, cancels when subscribed. Returns the new state. */
    @Transactional
    public boolean toggle(Long userId) {
        if (cancel(userId)) {
            return false;
        }
        subscribe(userId);
        return true;
    }
}
