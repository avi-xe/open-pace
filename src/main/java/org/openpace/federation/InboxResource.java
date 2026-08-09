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
import org.openpace.shared.ErrorResponse;
import org.openpace.shared.RsaKeyUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.tomitribe.auth.signatures.Signature;
import org.tomitribe.auth.signatures.Verifier;

/**
 * ActivityPub inbox endpoint.
 *
 * Handles both S2S (server-to-server) and C2S (client-to-server) inbox processing.
 * For Sprint 1, this handles S2S delivery from remote servers.
 */
@Path("/users/{username}/inbox")
public class InboxResource {

    private static final Logger LOG = Logger.getLogger(InboxResource.class.getName());

    @Inject
    InboxActivityProcessor inboxActivityProcessor;

    @Inject
    ActivityPubModelBuilder modelBuilder;

    @Inject
    ObjectMapper objectMapper;

    /**
     * POST to inbox — receive an activity from a remote server or client.
     */
    @POST
    @Consumes(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    @Transactional
    public Response postInbox(
            @PathParam("username") String username,
            @Context HttpHeaders headers,
            String body) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Received inbox activity for: " + username);

        try {
            // Verify HTTP Signature if present
            String signatureHeader = headers.getHeaderString("Signature");
            if (signatureHeader != null && !signatureHeader.isEmpty()) {
                boolean valid = verifySignature(headers, body);
                if (!valid) {
                    LOG.warning("Invalid signature from remote server");
                    return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("INVALID_SIGNATURE", "HTTP Signature verification failed"))
                        .build();
                }
                LOG.info("Signature verified successfully");
            } else {
                LOG.info("No signature present, accepting request (C2S or test)");
            }

            JsonNode activityJson = objectMapper.readTree(body);
            inboxActivityProcessor.processActivity(activityJson);
            return Response.accepted().build();
        } catch (Exception e) {
            LOG.warning("Failed to process inbox activity: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Bad Request", e.getMessage()))
                .build();
        }
    }

    /**
     * Verify HTTP Signature on inbound request.
     *
     * @param headers the HTTP headers
     * @param body the request body
     * @return true if signature is valid
     */
    private boolean verifySignature(HttpHeaders headers, String body) {
        try {
            // Parse the Signature header
            String signatureHeaderValue = headers.getHeaderString("Signature");
            Signature signature = Signature.fromString(signatureHeaderValue, org.tomitribe.auth.signatures.Algorithm.RSA_SHA256);
            
            // Get the keyId from the signature
            String keyId = signature.getKeyId();
            if (keyId == null || keyId.isEmpty()) {
                LOG.warning("No keyId in signature");
                return false;
            }
            
            // Extract actor URL from keyId (format: https://server/users/user#main-key)
            String actorUrl = keyId.contains("#") ? keyId.substring(0, keyId.indexOf("#")) : keyId;
            
            // Fetch the remote actor's public key
            java.security.PublicKey publicKey = fetchRemotePublicKey(actorUrl);
            if (publicKey == null) {
                LOG.warning("Could not fetch public key for: " + actorUrl);
                return false;
            }
            
            // Build headers map for verification
            Map<String, String> headerMap = new LinkedHashMap<>();
            headerMap.put("host", headers.getHeaderString("Host"));
            headerMap.put("date", headers.getHeaderString("Date"));
            headerMap.put("content-type", headers.getHeaderString("Content-Type"));
            
            // Create verifier and verify
            Verifier verifier = new Verifier(publicKey, signature);
            return verifier.verify("post", actorUrl, headerMap);
            
        } catch (Exception e) {
            LOG.warning("Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fetch remote actor's public key, using cache if available.
     *
     * @param actorUrl the remote actor's URL
     * @return the public key, or null if not found
     */
    private java.security.PublicKey fetchRemotePublicKey(String actorUrl) {
        try {
            // Check cache first
            RemoteActorKey cached = RemoteActorKey.findByActorUrl(actorUrl);
            if (cached != null && cached.isFresh()) {
                LOG.info("Using cached public key for: " + actorUrl);
                return RsaKeyUtils.parsePublicKey(cached.publicKey);
            }
            
            // Fetch from remote server
            LOG.info("Fetching public key from remote actor: " + actorUrl);
            jakarta.ws.rs.client.Client client = jakarta.ws.rs.client.ClientBuilder.newClient();
            try {
                String remoteActorJson = client.target(actorUrl)
                    .request("application/activity+json")
                    .get(String.class);
                
                JsonNode remoteActor = objectMapper.readTree(remoteActorJson);
                
                // Extract public key from actor
                JsonNode publicKeyNode = remoteActor.get("publicKey");
                if (publicKeyNode == null) {
                    LOG.warning("No publicKey in remote actor: " + actorUrl);
                    return null;
                }
                
                String publicKeyPem = publicKeyNode.get("publicKeyPem").asText();
                
                // Cache the key
                RemoteActorKey key = new RemoteActorKey();
                key.actorUrl = actorUrl;
                key.publicKey = publicKeyPem;
                key.fetchedAt = LocalDateTime.now();
                key.persist();
                
                LOG.info("Cached public key for: " + actorUrl);
                return RsaKeyUtils.parsePublicKey(publicKeyPem);
                
            } finally {
                client.close();
            }
        } catch (Exception e) {
            LOG.warning("Failed to fetch remote public key: " + e.getMessage());
            return null;
        }
    }

    /**
     * GET inbox — returns the inbox as an OrderedCollection.
     */
    @GET
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getInbox(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // For Sprint 1, return an empty inbox collection
        ActivityPubModels.OrderedCollection inbox = new ActivityPubModels.OrderedCollection();
        inbox.context = "https://www.w3.org/ns/activitystreams";
        inbox.type = "OrderedCollection";
        inbox.id = actor.getInboxUrl(modelBuilder.getBaseUrl());
        inbox.totalItems = "0";
        inbox.first = null;

        return Response.ok(inbox).build();
    }
}
