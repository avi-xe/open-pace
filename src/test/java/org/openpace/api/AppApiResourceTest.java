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
package org.openpace.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;
import org.openpace.auth.User;

/**
 * Tests for the application-friendly JSON API endpoints.
 * Uses embedded test user (testuser/testuser) for authentication.
 */
@QuarkusTest
class AppApiResourceTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testuser";

    @Inject
    UserTransaction tx;

    /**
     * Create a user and actor directly in the DB.
     */
    private void createUser(String username) throws Exception {
        tx.begin();
        User user = User.createFromOidc(username, username + "@test.com", username);
        user.persist();
        Actor actor = new Actor(username, username);
        actor.userId = user.id;
        actor.persist();
        tx.commit();
    }

    @Test
    void shouldReturnProfileForExistingUser() throws Exception {
        String username = "api-profile-alice";
        createUser(username);

        given()
            .pathParam("username", username)
        .when()
            .get("/api/users/{username}/profile")
        .then()
            .statusCode(200)
            .body("username", equalTo(username))
            .body("isLocal", equalTo(true))
            .body("followers", notNullValue())
            .body("activityCount", notNullValue());
    }

    @Test
    void shouldReturn404ForNonexistentProfile() {
        given()
            .pathParam("username", "api-profile-nonexistent")
        .when()
            .get("/api/users/{username}/profile")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturnFollowersList() throws Exception {
        String username = "api-followers-bob";
        createUser(username);

        given()
            .pathParam("username", username)
        .when()
            .get("/api/users/{username}/followers")
        .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void shouldReturn404ForNonexistentFollowers() {
        given()
            .pathParam("username", "api-followers-nonexistent")
        .when()
            .get("/api/users/{username}/followers")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturnFeedAsArray() {
        given()
        .when()
            .get("/api/feed")
        .then()
            .statusCode(200)
            .body("size()", notNullValue());
    }

    @Test
    void shouldReturnInstancesList() {
        given()
        .when()
            .get("/api/federation/instances")
        .then()
            .statusCode(200)
            .body("size()", notNullValue())
            .body("[0].isLocal", equalTo(true));
    }

    @Test
    void shouldRejectFollowWithoutAuth() {
        String body = "{\"actorUrl\":\"https://mastodon.social/users/alice\",\"username\":\"api-follow-charlie\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/federation/follow")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldAcceptFollowWithAuth() throws Exception {
        // testuser exists in DB via import.sql
        String body = "{\"actorUrl\":\"https://mastodon.social/users/alice\",\"username\":\"" + TEST_USER + "\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .auth().basic(TEST_USER, TEST_PASSWORD)
        .when()
            .post("/api/federation/follow")
        .then()
            .statusCode(200)
            .body("status", equalTo("pending"));
    }

    @Test
    void shouldRejectFollowWithoutActorUrl() throws Exception {
        // testuser exists in DB via import.sql
        String body = "{\"username\":\"" + TEST_USER + "\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .auth().basic(TEST_USER, TEST_PASSWORD)
        .when()
            .post("/api/federation/follow")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnFollowingList() throws Exception {
        String username = "api-following-frank";
        createUser(username);

        given()
            .pathParam("username", username)
        .when()
            .get("/api/users/{username}/following")
        .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }
}
