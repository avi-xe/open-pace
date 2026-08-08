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
package org.openpace.actor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ActorResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnActorProfile() throws Exception {
        tx.begin();
        Actor actor = new Actor("actor-alice", "Alice");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/actor-alice")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("Person"))
            .body("preferredUsername", equalTo("actor-alice"))
            .body("name", equalTo("Alice"))
            .body("id", notNullValue())
            .body("inbox", notNullValue())
            .body("outbox", notNullValue());
    }

    @Test
    void shouldReturn404ForNonexistentActor() {
        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/nonexistent-actor")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldIncludeFollowersCollection() throws Exception {
        tx.begin();
        Actor actor = new Actor("actor-bob", "Bob");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/actor-bob")
        .then()
            .statusCode(200)
            .body("followers", notNullValue())
            .body("following", notNullValue());
    }
}
