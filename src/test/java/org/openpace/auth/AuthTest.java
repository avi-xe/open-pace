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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for auth endpoints.
 * Uses embedded test user (testuser/testuser) for authentication.
 * The testuser is created in import.sql in the users table.
 */
@QuarkusTest
class AuthTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testuser";

    @Test
    void shouldListProviders() {
        given()
        .when()
            .get("/api/auth/login")
        .then()
            .statusCode(200)
            .body("providers.size()", equalTo(2));
    }

    @Test
    void shouldReturnUserInfo() {
        // testuser exists in DB via import.sql
        // GET /api/auth/me with Basic Auth
        given()
            .auth().basic(TEST_USER, TEST_PASSWORD)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(200)
            .body("username", equalTo(TEST_USER))
            .body("id", notNullValue());
    }

    @Test
    void shouldRejectLoginWithoutInstance() {
        given()
        .when()
            .get("/api/auth/login/mastodon")
        .then()
            .statusCode(400);
    }
}
