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
package org.openpace.core;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OutboxResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnOutboxCollection() throws Exception {
        tx.begin();
        Actor actor = new Actor("outbox-alice", "Alice");
        actor.persist();
        tx.commit();

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
        tx.begin();
        Actor actor = new Actor("outbox-bob", "Bob");
        actor.persist();
        tx.commit();

        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/outbox-bob/outbox")
        .then()
            .statusCode(202)
            .body("id", notNullValue());
    }

    @Test
    void shouldReturn404ForNonexistentActor() {
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/outbox-nonexistent/outbox")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn400WhenTypeMissing() {
        Map<String, Object> activity = Map.of(
            "object", Map.of(
                "type", "Note",
                "content", "Hello World"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/outbox-alice/outbox")
        .then()
            .statusCode(400);
    }
}
