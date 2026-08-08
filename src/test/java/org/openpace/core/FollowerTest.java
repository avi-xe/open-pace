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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FollowerTest {

    @Test
    @TestTransaction
    void shouldCreateFollower() {
        Actor actor = new Actor("follower-alice", "Alice");
        actor.persist();

        Follower follower = new Follower();
        follower.actor = actor;
        follower.followerActorUrl = "http://remote.example/users/bob";
        follower.followerInbox = "http://remote.example/users/bob/inbox";
        follower.persist();

        assertNotNull(follower.id);
        assertEquals("http://remote.example/users/bob", follower.followerActorUrl);
        assertEquals("http://remote.example/users/bob/inbox", follower.followerInbox);
    }

    @Test
    @TestTransaction
    void shouldFindFollowersByActor() {
        Actor actor = new Actor("follower-alice2", "Alice");
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

        List<Follower> followers = Follower.findByActor(actor);
        assertEquals(2, followers.size());
    }

    @Test
    @TestTransaction
    void shouldCountFollowersByActor() {
        Actor actor = new Actor("follower-alice3", "Alice");
        actor.persist();

        Follower f1 = new Follower();
        f1.actor = actor;
        f1.followerActorUrl = "http://remote1.example/users/bob";
        f1.followerInbox = "http://remote1.example/users/bob/inbox";
        f1.persist();

        long count = Follower.countByActor(actor);
        assertEquals(1, count);
    }

    @Test
    @TestTransaction
    void shouldFindFollowerByActorAndUrl() {
        Actor actor = new Actor("follower-alice4", "Alice");
        actor.persist();

        Follower follower = new Follower();
        follower.actor = actor;
        follower.followerActorUrl = "http://remote.example/users/bob";
        follower.followerInbox = "http://remote.example/users/bob/inbox";
        follower.persist();

        Follower found = Follower.findByActorAndFollowerUrl(actor, "http://remote.example/users/bob");
        assertNotNull(found);
        assertEquals("http://remote.example/users/bob/inbox", found.followerInbox);
    }

    @Test
    @TestTransaction
    void shouldReturnNullForNonexistentFollower() {
        Actor actor = new Actor("follower-alice5", "Alice");
        actor.persist();

        Follower found = Follower.findByActorAndFollowerUrl(actor, "http://nonexistent");
        assertNull(found);
    }
}
