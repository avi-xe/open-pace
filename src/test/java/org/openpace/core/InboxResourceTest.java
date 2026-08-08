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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InboxResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnInboxCollection() throws Exception {
        tx.begin();
        Actor actor = new Actor("inbox-alice", "Alice");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/inbox-alice/inbox")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("OrderedCollection"))
            .body("totalItems", equalTo("0"));
    }

    @Test
    void shouldAcceptFollowActivity() throws Exception {
        tx.begin();
        Actor actor = new Actor("inbox-bob", "Bob");
        actor.persist();
        tx.commit();

        Map<String, Object> activity = Map.of(
            "type", "Follow",
            "actor", "http://remote.example/users/bob",
            "object", "http://localhost:8080/users/inbox-bob"
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/inbox-bob/inbox")
        .then()
            .statusCode(202);
    }

    @Test
    void shouldAcceptCreateActivity() throws Exception {
        tx.begin();
        Actor actor = new Actor("inbox-charlie", "Charlie");
        actor.persist();
        tx.commit();

        Map<String, Object> activity = Map.of(
            "type", "Create",
            "actor", "http://remote.example/users/bob",
            "object", Map.of(
                "type", "Note",
                "content", "Hello from remote!"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/inbox-charlie/inbox")
        .then()
            .statusCode(202);
    }

    @Test
    void shouldReturn404ForNonexistentActor() {
        Map<String, Object> activity = Map.of(
            "type", "Create",
            "actor", "http://remote.example/users/bob",
            "object", Map.of(
                "type", "Note",
                "content", "Hello!"
            )
        );

        given()
            .contentType("application/activity+json")
            .body(activity)
        .when()
            .post("/users/inbox-nonexistent/inbox")
        .then()
            .statusCode(404);
    }
}
