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
package org.openpace.webfinger;

import org.openpace.actor.Actor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WebFingerResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnWebFingerForExistingActor() throws Exception {
        tx.begin();
        Actor actor = new Actor("wf-alice", "Alice");
        actor.persist();
        tx.commit();

        given()
            .queryParam("resource", "acct:wf-alice@localhost:8080")
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(200)
            .contentType("application/jrd+json")
            .body("subject", equalTo("acct:wf-alice@localhost:8080"))
            .body("aliases", hasSize(1))
            .body("links", hasSize(1))
            .body("links[0].rel", equalTo("self"))
            .body("links[0].type", equalTo("application/activity+json"));
    }

    @Test
    void shouldReturn404ForNonexistentActor() {
        given()
            .queryParam("resource", "acct:wf-nonexistent@localhost:8080")
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn400WhenResourceParamMissing() {
        given()
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenResourceParamBlank() {
        given()
            .queryParam("resource", "")
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn404WhenResourceNotAcctScheme() {
        given()
            .queryParam("resource", "https://localhost:8080/users/alice")
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn404ForMalformedAcctUri() {
        given()
            .queryParam("resource", "acct:wf-bob")
        .when()
            .get("/.well-known/webfinger")
        .then()
            .statusCode(404);
    }
}
