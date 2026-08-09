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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RsaKeyUtils - HTTP Signature key generation.
 */
class RsaKeyUtilsTest {

    @Test
    void shouldGenerateKeyPair() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        assertEquals("RSA", keyPair.getPublic().getAlgorithm());
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    void shouldConvertPublicKeyToPem() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        String pem = RsaKeyUtils.publicKeyToPem(keyPair.getPublic());
        
        assertNotNull(pem);
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pem.endsWith("-----END PUBLIC KEY-----"));
        assertTrue(pem.contains("\n"));
    }

    @Test
    void shouldConvertPrivateKeyToPem() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        String pem = RsaKeyUtils.privateKeyToPem(keyPair.getPrivate());
        
        assertNotNull(pem);
        assertTrue(pem.startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertTrue(pem.endsWith("-----END RSA PRIVATE KEY-----"));
        assertTrue(pem.contains("\n"));
    }

    @Test
    void shouldRoundTripPublicKey() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        String pem = RsaKeyUtils.publicKeyToPem(keyPair.getPublic());
        PublicKey parsed = RsaKeyUtils.parsePublicKey(pem);
        
        assertNotNull(parsed);
        assertEquals(keyPair.getPublic(), parsed);
    }

    @Test
    void shouldRoundTripPrivateKey() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        String pem = RsaKeyUtils.privateKeyToPem(keyPair.getPrivate());
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(pem);
        
        assertNotNull(parsed);
        assertEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    void shouldGenerate2048BitKeys() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        
        // RSA 2048-bit public key encoded size includes ASN.1 header
        // The key modulus is 256 bytes, but X509 encoding adds overhead
        assertTrue(keyPair.getPublic().getEncoded().length > 200);
        assertTrue(keyPair.getPrivate().getEncoded().length > 200);
    }
}
