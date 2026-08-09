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

import org.openpace.actor.Actor;
import org.openpace.federation.protocol.ActivityPubModels;
import org.openpace.shared.RsaKeyUtils;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.security.Key;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.tomitribe.auth.signatures.Algorithm;
import org.tomitribe.auth.signatures.Signature;
import org.tomitribe.auth.signatures.Signer;

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
     * Deliver an activity to a remote inbox with HTTP Signature.
     *
     * @param targetInbox the target actor's inbox URL
     * @param activityJson the serialized activity JSON
     * @param actor the actor signing the request (for private key)
     */
    public void deliver(String targetInbox, String activityJson, Actor actor) {
        LOG.info("Delivering activity to: " + targetInbox);

        try {
            io.vertx.core.json.JsonObject jsonBody = new io.vertx.core.json.JsonObject(activityJson);

            // Create date header for signing
            String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());

            // Build headers to sign
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("host", extractHost(targetInbox));
            headers.put("date", date);
            headers.put("content-type", "application/activity+json");

            // Sign the request if actor has a private key
            Map<String, String> signedHeaders = headers;
            if (actor != null && actor.privateKey != null) {
                try {
                    Key privateKey = RsaKeyUtils.parsePrivateKey(actor.privateKey);
                    String keyId = actor.getActorId("") + "#main-key";
                    
                    // Create signature configuration
                    Signature signatureConfig = new Signature(
                        keyId,
                        "rsa-sha256",
                        null,
                        null,
                        null,
                        java.util.Arrays.asList("(request-target)", "host", "date", "content-type")
                    );
                    
                    Signer signer = new Signer(privateKey, signatureConfig);
                    
                    // Create signing string and sign
                    String signingString = signer.createSigningString("post", targetInbox, headers);
                    Signature signed = signer.sign("post", targetInbox, headers);
                    
                    // Add signature headers
                    signedHeaders = new LinkedHashMap<>(headers);
                    signedHeaders.put("signature", signed.toString());
                    signedHeaders.put("authorization", "Signature keyId=\"" + keyId + "\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date content-type\",signature=\"" + signed.getSignature() + "\"");
                    
                    LOG.info("Signed request for actor: " + actor.username);
                } catch (Exception e) {
                    LOG.warning("Failed to sign request, sending unsigned: " + e.getMessage());
                }
            }

            HttpRequest<Buffer> request = webClient
                .request(HttpMethod.POST, targetInbox)
                .putHeader("Content-Type", "application/activity+json")
                .putHeader("Accept", "application/activity+json")
                .putHeader("Date", date);

            // Add signature header if present
            if (signedHeaders.containsKey("signature")) {
                request.putHeader("Signature", signedHeaders.get("signature"));
            }
            if (signedHeaders.containsKey("authorization")) {
                request.putHeader("Authorization", signedHeaders.get("authorization"));
            }

            request.sendJsonObject(jsonBody, response -> {
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
            });
        } catch (Exception e) {
            LOG.warning("Failed to parse activity JSON for delivery: " + e.getMessage());
        }
    }

    /**
     * Extract host from URL for signing.
     */
    private String extractHost(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Deliver an activity as a JSON string to a remote inbox with HTTP Signature.
     *
     * @param targetInbox the target actor's inbox URL
     * @param activity the activity model to serialize and send
     * @param actor the actor signing the request (for private key)
     */
    public void deliverActivity(String targetInbox, ActivityPubModels.Activity activity, Actor actor) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(activity);
            deliver(targetInbox, json, actor);
        } catch (Exception e) {
            LOG.warning("Failed to serialize activity for delivery: " + e.getMessage());
        }
    }
}
