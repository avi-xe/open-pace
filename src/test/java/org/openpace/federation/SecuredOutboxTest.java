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
 * Verifies that the outbox POST endpoint requires authentication
 * and enforces username ownership.
 */
@QuarkusTest
class SecuredOutboxTest {

    private static final String PASSWORD = "password123";

    private void register(String username) {
        given()
            .contentType("application/json")
            .body(Map.of(
                "username", username,
                "password", PASSWORD,
                "email", username + "@test.com"
            ))
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201);
    }

    @Test
    void shouldRejectUnauthenticatedPost() {
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/secbox-alice/outbox")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectWrongUser() {
        register("secbox-bob");

        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        given()
            .auth().basic("secbox-bob", PASSWORD)
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/secbox-alice/outbox")
        .then()
            .statusCode(403);
    }

    @Test
    void shouldAllowAuthenticatedPost() {
        register("secbox-alice");

        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello"
            )
        );

        given()
            .auth().basic("secbox-alice", PASSWORD)
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/secbox-alice/outbox")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }
}
