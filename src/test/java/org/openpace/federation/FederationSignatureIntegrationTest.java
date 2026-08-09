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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.UserTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openpace.actor.Actor;

/**
 * Integration tests for HTTP Signature federation flow.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederationSignatureIntegrationTest {

    private static final String TEST_USERNAME = "sigtest-user";

    @Inject
    UserTransaction transaction;

    @Test
    @Order(1)
    void shouldReturnPublicKeyForActor() throws Exception {
        // Create actor with keys
        transaction.begin();
        Actor actor = new Actor(TEST_USERNAME, "Signature Test User");
        java.security.KeyPair keyPair = org.openpace.shared.RsaKeyUtils.generateKeyPair();
        actor.publicKey = org.openpace.shared.RsaKeyUtils.publicKeyToPem(keyPair.getPublic());
        actor.privateKey = org.openpace.shared.RsaKeyUtils.privateKeyToPem(keyPair.getPrivate());
        actor.persist();
        transaction.commit();

        // Get public key
        given()
            .when()
            .get("/users/{username}/public-key", TEST_USERNAME)
            .then()
            .statusCode(200)
            .contentType(ContentType.TEXT)
            .body(containsString("-----BEGIN PUBLIC KEY-----"))
            .body(containsString("-----END PUBLIC KEY-----"));
    }

    @Test
    @Order(2)
    void shouldReturnActorWithPublicKey() {
        // Get actor profile
        given()
            .accept("application/activity+json")
            .when()
            .get("/users/{username}", TEST_USERNAME)
            .then()
            .statusCode(200)
            .body("publicKey.id", notNullValue())
            .body("publicKey.owner", notNullValue())
            .body("publicKey.publicKeyPem", containsString("-----BEGIN PUBLIC KEY-----"));
    }

    @Test
    @Order(3)
    void shouldReturn404ForNonexistentActor() {
        given()
            .when()
            .get("/users/{username}/public-key", "nonexistent-user-12345")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(4)
    void shouldIncludePublicKeyInActorEndpoint() {
        given()
            .accept("application/activity+json")
            .when()
            .get("/users/{username}", TEST_USERNAME)
            .then()
            .statusCode(200)
            .body("publicKey.id", containsString("#main-key"))
            .body("publicKey.owner", containsString("/users/" + TEST_USERNAME));
    }
}
