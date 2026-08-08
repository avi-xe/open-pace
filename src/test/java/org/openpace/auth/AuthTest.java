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

@QuarkusTest
class AuthTest {

    @Test
    void shouldRegisterUser() {
        given()
            .contentType("application/json")
            .body("{\"username\":\"auth-alice\",\"password\":\"password123\",\"email\":\"alice@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("username", equalTo("auth-alice"));
    }

    @Test
    void shouldRejectShortPassword() {
        given()
            .contentType("application/json")
            .body("{\"username\":\"auth-shortpw\",\"password\":\"short\",\"email\":\"short@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectBlankUsername() {
        given()
            .contentType("application/json")
            .body("{\"username\":\"\",\"password\":\"password123\",\"email\":\"blank@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectDuplicateUsername() {
        // Register first time — should succeed
        given()
            .contentType("application/json")
            .body("{\"username\":\"auth-dup\",\"password\":\"password123\",\"email\":\"dup@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201);

        // Register same username again — should conflict
        given()
            .contentType("application/json")
            .body("{\"username\":\"auth-dup\",\"password\":\"password123\",\"email\":\"dup2@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409);
    }

    @Test
    void shouldReturnCurrentUser() {
        // Register a user first
        given()
            .contentType("application/json")
            .body("{\"username\":\"auth-me\",\"password\":\"password123\",\"email\":\"me@test.com\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201);

        // GET /api/auth/me with Basic Auth
        given()
            .auth().basic("auth-me", "password123")
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(200)
            .body("username", equalTo("auth-me"))
            .body("id", notNullValue())
            .body("email", equalTo("me@test.com"));
    }

    @Test
    void shouldReturn401ForUnauthenticatedMe() {
        given()
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(401);
    }
}
