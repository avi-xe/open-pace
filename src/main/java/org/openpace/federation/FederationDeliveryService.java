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

import org.openpace.activity.models.ActivityPubModels;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.logging.Logger;

/**
 * Delivers activities to remote inboxes using Vert.x WebClient.
 *
 * Sprint 1 uses fire-and-forget delivery. Reliable delivery with Redis queues
 * and retry mechanisms will be added in Sprint 5.
 *
 * @see <a href="https://www.w3.org/TR/activitypub/#delivery">ActivityPub Delivery</a>
 */
@Singleton
public class FederationDeliveryService {

    private static final Logger LOG = Logger.getLogger(FederationDeliveryService.class.getName());

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx);
        LOG.info("FederationDeliveryService initialized with Vert.x WebClient");
    }

    /**
     * Deliver an activity to a remote inbox.
     *
     * @param targetInbox the target actor's inbox URL
     * @param activityJson the serialized activity JSON
     */
    public void deliver(String targetInbox, String activityJson) {
        LOG.info("Delivering activity to: " + targetInbox);

        HttpRequest<Buffer> request = webClient
            .request(HttpMethod.POST, targetInbox)
            .putHeader("Content-Type", "application/activity+json")
            .putHeader("Accept", "application/activity+json");

        request.sendJsonObject(
            io.vertx.core.json.JsonObject.mapFrom(
                new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                    com.fasterxml.jackson.databind.ObjectMapper.class,
                    com.fasterxml.jackson.databind.node.ObjectNode.class
                )
            ),
            response -> {
                if (response.succeeded()) {
                    HttpResponse<Buffer> result = response.result();
                    if (result.statusCode() >= 200 && result.statusCode() < 300) {
                        LOG.info("Successfully delivered to " + targetInbox);
                    } else {
                        LOG.warning("Delivery failed with status " + result.statusCode() + " to " + targetInbox);
                    }
                } else {
                    LOG.warning("Delivery error to " + targetInbox + ": " + response.cause().getMessage());
                }
            }
        );
    }

    /**
     * Deliver an activity as a JSON string to a remote inbox.
     *
     * @param targetInbox the target actor's inbox URL
     * @param activity the activity model to serialize and send
     */
    public void deliverActivity(String targetInbox, ActivityPubModels.Activity activity) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(activity);
            deliver(targetInbox, json);
        } catch (Exception e) {
            LOG.warning("Failed to serialize activity for delivery: " + e.getMessage());
        }
    }
}
