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
package org.openpace.social;

import org.openpace.actor.Actor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FollowersResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnFollowersCollection() throws Exception {
        tx.begin();
        Actor actor = new Actor("followers-alice", "Alice");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/followers-alice/followers")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("OrderedCollection"))
            .body("totalItems", equalTo("0"));
    }

    @Test
    void shouldReturnFollowersPage() throws Exception {
        tx.begin();
        Actor actor = new Actor("followers-bob", "Bob");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/followers-bob/followers/page")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("OrderedCollectionPage"));
    }

    @Test
    void shouldReturnFollowingCollection() throws Exception {
        tx.begin();
        Actor actor = new Actor("followers-charlie", "Charlie");
        actor.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/followers-charlie/following")
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("OrderedCollection"))
            .body("totalItems", equalTo("0"));
    }

    @Test
    void shouldReturn404ForNonexistentActor() {
        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/followers-nonexistent/followers")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldCountFollowersCorrectly() throws Exception {
        tx.begin();
        Actor actor = new Actor("followers-dave", "Dave");
        actor.persist();

        Follower f1 = new Follower();
        f1.actor = actor;
        f1.followerActorUrl = "http://remote1.example/users/bob";
        f1.followerInbox = "http://remote1.example/users/bob/inbox";
        f1.persist();

        Follower f2 = new Follower();
        f2.actor = actor;
        f2.followerActorUrl = "http://remote2.example/users/charlie";
        f2.followerInbox = "http://remote2.example/users/charlie/inbox";
        f2.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/followers-dave/followers")
        .then()
            .statusCode(200)
            .body("totalItems", equalTo("2"));
    }
}
