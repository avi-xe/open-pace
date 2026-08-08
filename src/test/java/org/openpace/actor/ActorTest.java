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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ActorTest {

    @Test
    @TestTransaction
    void shouldCreateActor() {
        Actor actor = new Actor("atest-create-alice", "Alice");
        actor.persist();

        assertNotNull(actor.id);
        assertEquals("atest-create-alice", actor.username);
        assertEquals("Alice", actor.name);
        assertNotNull(actor.createdAt);
    }

    @Test
    @TestTransaction
    void shouldFindActorByUsername() {
        Actor actor = new Actor("atest-find-bob", "Bob");
        actor.persist();

        Actor found = Actor.findByUsername("atest-find-bob");
        assertNotNull(found);
        assertEquals("atest-find-bob", found.username);
        assertEquals("Bob", found.name);
    }

    @Test
    @TestTransaction
    void shouldReturnNullForNonexistentUsername() {
        Actor found = Actor.findByUsername("atest-nonexistent");
        assertNull(found);
    }

    @Test
    @TestTransaction
    void shouldGenerateActorId() {
        Actor actor = new Actor("atest-url-charlie", "Charlie");
        actor.persist();

        String baseUrl = "http://localhost:8080";
        String actorId = actor.getActorId(baseUrl);
        assertEquals("http://localhost:8080/users/atest-url-charlie", actorId);
    }

    @Test
    @TestTransaction
    void shouldGenerateInboxUrl() {
        Actor actor = new Actor("atest-inbox-dave", "Dave");
        actor.persist();

        String baseUrl = "http://localhost:8080";
        String inboxUrl = actor.getInboxUrl(baseUrl);
        assertEquals("http://localhost:8080/users/atest-inbox-dave/inbox", inboxUrl);
    }

    @Test
    @TestTransaction
    void shouldGenerateOutboxUrl() {
        Actor actor = new Actor("atest-outbox-eve", "Eve");
        actor.persist();

        String baseUrl = "http://localhost:8080";
        String outboxUrl = actor.getOutboxUrl(baseUrl);
        assertEquals("http://localhost:8080/users/atest-outbox-eve/outbox", outboxUrl);
    }
}
