/*
 * Copyright 2024 Open Pace Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openpace.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Maps an external OIDC/OAuth2 provider identity to a local User.
 *
 * Each row represents one login provider (e.g., "generic-oidc", "mastodon")
 * linked to the provider's unique user ID and the local User account.
 */
@Entity
@Table(name = "external_identity", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
})
public class ExternalIdentity extends PanacheEntity {

    @Column(nullable = false, length = 50)
    public String provider;

    @Column(name = "provider_user_id", nullable = false)
    public String providerUserId;

    @Column
    public String email;

    @Column
    public String username;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "avatar_url", length = 500)
    public String avatarUrl;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    public ExternalIdentity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Find an existing identity by provider and external user ID.
     */
    public static ExternalIdentity findByProviderAndExternalId(String provider, String providerUserId) {
        return find("provider = ?1 and providerUserId = ?2", provider, providerUserId).firstResult();
    }

    /**
     * Find all identities for a given user.
     */
    public static java.util.List<ExternalIdentity> findByUser(User user) {
        return find("user", user).list();
    }

    /**
     * Find an identity by email (for auto-linking during first OIDC login).
     */
    public static ExternalIdentity findByEmail(String email) {
        return find("email = ?1", email).firstResult();
    }
}
