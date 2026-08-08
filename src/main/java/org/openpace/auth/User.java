/*
 * Copyright 2024 Open Pace
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
import jakarta.persistence.Table;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;

@Entity
@Table(name = "users")
@UserDefinition
public class User extends PanacheEntity {

    @Username
    @Column(unique = true, nullable = false)
    public String username;

    @Password
    @Column(nullable = false)
    public String password;

    @Column
    public String email;

    @Column(nullable = false)
    public Boolean verified = true;

    @Roles
    @Column(nullable = false, length = 50)
    public String role = "user";

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public static User create(String username, String plainPassword, String role) {
        User user = new User();
        user.username = username;
        user.password = BcryptUtil.bcryptHash(plainPassword);
        user.role = role;
        user.persist();
        return user;
    }
}
