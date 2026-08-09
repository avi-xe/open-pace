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
package org.openpace.federation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for outbox endpoint.
 * Uses embedded test user (testuser/testuser) for authentication.
 * The testuser is created in import.sql in the users table.
 */
@QuarkusTest
class SecuredOutboxTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testuser";

    @Test
    void shouldCreateActivity() {
        // testuser exists in DB via import.sql
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        given()
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/" + TEST_USER + "/outbox")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    void shouldRejectMissingType() {
        Map<String, Object> activity = Map.of(
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        given()
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/" + TEST_USER + "/outbox")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectWrongUser() {
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        // Authenticated as testuser, posting to wronguser's outbox
        given()
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/wronguser/outbox")
        .then()
            .statusCode(403);
    }
}
