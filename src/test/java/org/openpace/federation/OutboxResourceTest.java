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

import org.openpace.actor.Actor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the outbox endpoint.
 * Uses embedded test user (testuser/testuser) for authentication.
 * Creates actors directly in the DB for outbox tests.
 */
@QuarkusTest
class OutboxResourceTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testuser";

    @Inject
    UserTransaction tx;

    private void createActor(String username) throws Exception {
        tx.begin();
        Actor actor = new Actor(username, username);
        actor.persist();
        tx.commit();
    }

    @Test
    void shouldReturnOutboxCollection() throws Exception {
        createActor("outbox-alice");

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/outbox-alice/outbox")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("OrderedCollection"))
            .body("totalItems", equalTo("0"));
    }

    @Test
    void shouldAcceptCreateActivity() throws Exception {
        // testuser exists in DB via import.sql, with linked actor
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        given()
            .contentType("application/activity+json")
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .body(activity)
        .when()
            .post("/users/" + TEST_USER + "/outbox")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    void shouldReturn403ForWrongUser() throws Exception {
        createActor("outbox-ghost");

        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        // Authenticated as testuser but posting to outbox-ghost's outbox
        given()
            .contentType("application/activity+json")
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .body(activity)
        .when()
            .post("/users/outbox-ghost/outbox")
        .then()
            .statusCode(403);
    }

    @Test
    void shouldReturn400WhenTypeMissing() throws Exception {
        // testuser exists in DB via import.sql
        Map<String, Object> activity = Map.of(
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        given()
            .contentType("application/activity+json")
            .auth().basic(TEST_USER, TEST_PASSWORD)
            .body(activity)
        .when()
            .post("/users/" + TEST_USER + "/outbox")
        .then()
            .statusCode(400);
    }
}
