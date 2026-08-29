package com.webhookplatform.webhook.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "google_subject", nullable = false, unique = true, length = 255, updatable = false)
    private String googleSubject;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    static User create(String googleSubject, String email, String displayName, String avatarUrl, Instant now) {
        User user = new User();
        user.googleSubject = googleSubject;
        user.email = email;
        user.displayName = displayName;
        user.avatarUrl = avatarUrl;
        user.status = UserStatus.ACTIVE;
        user.lastLoginAt = now;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    void recordLogin(String email, String displayName, String avatarUrl, Instant now) {
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
