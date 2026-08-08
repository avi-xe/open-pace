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
package org.openpace.activity.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * ActivityPub JSON model classes for serialization/deserialization.
 *
 * IMPORTANT: Avoid importing these inner classes directly — use fully qualified names
 * (e.g., ActivityPubModels.Activity) to prevent shadowing the database entity
 * {@link org.openpace.core.Activity}. See DATABASE_DESIGN.md for details.
 */
public class ActivityPubModels {

    /**
     * ActivityStreams actor types.
     */
    public static final String PERSON_TYPE = "Person";
    public static final String APPLICATION_TYPE = "Application";

    /**
     * ActivityStreams activity types.
     */
    public static final String CREATE_TYPE = "Create";
    public static final String FOLLOW_TYPE = "Follow";
    public static final String ACCEPT_TYPE = "Accept";
    public static final String UNDO_TYPE = "Undo";
    public static final String LIKE_TYPE = "Like";
    public static final String ANNOUNCE_TYPE = "Announce";

    /**
     * Content types.
     */
    public static final String APPLICATION_ACTIVITY_JSON = "application/activity+json";

    /**
     * Standard ActivityStreams context.
     */
    public static final Map<String, String> ACTIVITY_STREAMS_CONTEXT = Map.of(
        "@context", "https://www.w3.org/ns/activitystreams"
    );

    /**
     * ActivityPub actor model (Person/Application).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Actor {
        @JsonProperty("@context")
        public Object context;
        public String type;
        public String id;
        public String preferredUsername;
        public String name;
        public String summary;
        public Inbox inbox;
        public String outbox;
        public Map<String, String> followers;
        public Map<String, String> following;
        public String publicKey;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Inbox {
            public String type;
            public String totalItems;
            public boolean first;
        }
    }

    /**
     * ActivityPub activity model.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Activity {
        @JsonProperty("@context")
        public Object context;
        public String type;
        public String id;
        public String actor;
        public Object object;
        public String published;
        public Map<String, String> target;

        /**
         * For Follow activities: the target actor URL.
         */
        @JsonProperty("target")
        public void setTarget(Map<String, String> target) {
            this.target = target;
        }

        /**
         * For Follow activities: the target actor URL.
         */
        @JsonProperty("target")
        public Map<String, String> getTarget() {
            return target;
        }
    }

    /**
     * ActivityPub Note object model.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Note {
        @JsonProperty("@context")
        public Object context;
        public String type;
        public String id;
        public String content;
        public String attributedTo;
        public String published;
        public String inReplyTo;
    }

    /**
     * ActivityPub OrderedCollection model.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderedCollection {
        @JsonProperty("@context")
        public Object context;
        public String type;
        public String id;
        public String totalItems;
        public String first;
        public String last;
    }

    /**
     * ActivityPub OrderedCollectionPage model.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderedCollectionPage {
        @JsonProperty("@context")
        public Object context;
        public String type;
        public String id;
        public String next;
        public String partOf;
        public List<String> orderedItems;
    }
}
