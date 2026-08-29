package com.webhookplatform.webhook.user;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public User synchronizeGoogleUser(String googleSubject, String email, String displayName, String avatarUrl) {
        Instant now = clock.instant();
        return userRepository.findByGoogleSubject(googleSubject)
                .map(user -> updateExisting(user, email, displayName, avatarUrl, now))
                .orElseGet(() -> userRepository.saveAndFlush(
                        User.create(googleSubject, email, displayName, avatarUrl, now)));
    }

    @Transactional
    public User synchronizeExistingGoogleUser(
            String googleSubject,
            String email,
            String displayName,
            String avatarUrl) {
        User user = userRepository.findByGoogleSubject(googleSubject)
                .orElseThrow(() -> new IllegalStateException("Concurrent user provisioning did not create a user"));
        return updateExisting(user, email, displayName, avatarUrl, clock.instant());
    }

    private User updateExisting(User user, String email, String displayName, String avatarUrl, Instant now) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new DisabledException("Local user is disabled");
        }
        user.recordLogin(email, displayName, avatarUrl, now);
        return user;
    }
}
