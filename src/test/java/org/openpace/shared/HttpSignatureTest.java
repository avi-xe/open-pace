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
package org.openpace.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomitribe.auth.signatures.Algorithm;
import org.tomitribe.auth.signatures.Signature;
import org.tomitribe.auth.signatures.Signer;
import org.tomitribe.auth.signatures.Verifier;

/**
 * Unit tests for HTTP Signature signing and verification.
 */
class HttpSignatureTest {

    @Test
    void shouldSignAndVerifyRequest() throws Exception {
        // Generate key pair
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Create signature configuration
        String keyId = "https://example.com/users/testuser#main-key";
        Signature signatureConfig = new Signature(
            keyId,
            null,
            Algorithm.RSA_SHA256,
            null,
            "rsa-sha256",
            Arrays.asList("(request-target)", "host", "date", "content-type")
        );

        // Create signer
        Signer signer = new Signer(privateKey, signatureConfig);

        // Build headers
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "mastodon.social");
        headers.put("date", "Sat, 09 Aug 2026 10:00:00 GMT");
        headers.put("content-type", "application/activity+json");

        // Sign the request
        Signature signed = signer.sign("post", "https://mastodon.social/users/remoteuser/inbox", headers);

        // Verify signature was created
        assertNotNull(signed);
        assertNotNull(signed.getSignature());
        assertTrue(signed.getSignature().length() > 0);

        // Create verifier and verify
        Verifier verifier = new Verifier(publicKey, signed);
        boolean valid = verifier.verify("post", "https://mastodon.social/users/remoteuser/inbox", headers);

        assertTrue(valid, "Signature should be valid");
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        // Generate two different key pairs
        KeyPair keyPair1 = RsaKeyUtils.generateKeyPair();
        KeyPair keyPair2 = RsaKeyUtils.generateKeyPair();

        // Sign with key pair 1
        Signature signatureConfig = new Signature(
            "https://example.com/users/testuser#main-key",
            null,
            Algorithm.RSA_SHA256,
            null,
            "rsa-sha256",
            Arrays.asList("(request-target)", "host", "date")
        );

        Signer signer = new Signer(keyPair1.getPrivate(), signatureConfig);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "mastodon.social");
        headers.put("date", "Sat, 09 Aug 2026 10:00:00 GMT");

        Signature signed = signer.sign("post", "https://mastodon.social/inbox", headers);

        // Try to verify with key pair 2 (wrong key)
        Verifier verifier = new Verifier(keyPair2.getPublic(), signed);
        boolean valid = verifier.verify("post", "https://mastodon.social/inbox", headers);

        assertFalse(valid, "Signature should be invalid with wrong key");
    }

    @Test
    void shouldRejectTamperedHeaders() throws Exception {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();

        Signature signatureConfig = new Signature(
            "https://example.com/users/testuser#main-key",
            null,
            Algorithm.RSA_SHA256,
            null,
            "rsa-sha256",
            Arrays.asList("(request-target)", "host", "date")
        );

        Signer signer = new Signer(keyPair.getPrivate(), signatureConfig);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "mastodon.social");
        headers.put("date", "Sat, 09 Aug 2026 10:00:00 GMT");

        Signature signed = signer.sign("post", "https://mastodon.social/inbox", headers);

        // Tamper with headers
        Map<String, String> tamperedHeaders = new LinkedHashMap<>(headers);
        tamperedHeaders.put("date", "Sat, 09 Aug 2026 11:00:00 GMT"); // Different time

        Verifier verifier = new Verifier(keyPair.getPublic(), signed);
        boolean valid = verifier.verify("post", "https://mastodon.social/inbox", tamperedHeaders);

        assertFalse(valid, "Signature should be invalid with tampered headers");
    }
}
