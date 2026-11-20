package com.gameflix.service;

import com.gameflix.model.Subscription;
import com.gameflix.model.SubscriptionStatus;
import com.gameflix.model.User;
import com.gameflix.repository.SubscriptionRepository;
import com.gameflix.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock UserRepository users;
    @InjectMocks SubscriptionService service;

    private User user() {
        return new User("fan@example.com", "hash", "Fan");
    }

    private void noActiveSubscription() {
        when(subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(5L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    @Test
    void userWithoutASubscriptionIsNotSubscribed() {
        noActiveSubscription();

        assertFalse(service.isSubscribed(5L));
    }

    @Test
    void userWithAnActiveSubscriptionIsSubscribed() {
        when(subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(5L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(new Subscription(user())));

        assertTrue(service.isSubscribed(5L));
    }

    @Test
    void subscribeCreatesAnActiveSubscription() {
        noActiveSubscription();
        when(users.getReferenceById(5L)).thenReturn(user());

        assertTrue(service.subscribe(5L));
        verify(subscriptions).saveAndFlush(any(Subscription.class));
    }

    @Test
    void subscribingWhileSubscribedDoesNothing() {
        when(subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(5L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(new Subscription(user())));

        assertFalse(service.subscribe(5L));
        verify(subscriptions, never()).saveAndFlush(any(Subscription.class));
    }

    @Test
    void cancelEndsTheActiveSubscription() {
        Subscription current = new Subscription(user());
        when(subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(5L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        assertTrue(service.cancel(5L));
        assertEquals(SubscriptionStatus.CANCELED, current.getStatus());
    }

    @Test
    void cancelWithoutASubscriptionReturnsFalse() {
        noActiveSubscription();

        assertFalse(service.cancel(5L));
    }

    @Test
    void toggleSubscribesWhenNotSubscribed() {
        noActiveSubscription();
        when(users.getReferenceById(5L)).thenReturn(user());

        assertTrue(service.toggle(5L));
        verify(subscriptions).saveAndFlush(any(Subscription.class));
    }

    @Test
    void toggleCancelsWhenSubscribed() {
        Subscription current = new Subscription(user());
        when(subscriptions.findFirstByUser_IdAndStatusOrderByIdDesc(5L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        assertFalse(service.toggle(5L));
        assertEquals(SubscriptionStatus.CANCELED, current.getStatus());
        verify(subscriptions, never()).saveAndFlush(any(Subscription.class));
    }
}
