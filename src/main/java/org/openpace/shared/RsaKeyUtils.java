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

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * RSA Key Pair utility for HTTP Signature authentication.
 * Generates 2048-bit RSA keys for ActivityPub federation.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures">HTTP Signatures</a>
 */
public class RsaKeyUtils {

    private static final Logger LOG = Logger.getLogger(RsaKeyUtils.class.getName());
    private static final int KEY_SIZE = 2048;

    /**
     * Generate an RSA key pair for a new actor.
     *
     * @return KeyPair containing public and private keys
     */
    public static KeyPair generateKeyPair() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            LOG.info("Generated RSA key pair for actor");
            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Convert a public key to PEM format for storage and transmission.
     *
     * @param publicKey the RSA public key
     * @return PEM-encoded string
     */
    public static String publicKeyToPem(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Convert a private key to PEM format for storage.
     *
     * @param privateKey the RSA private key
     * @return PEM-encoded string
     */
    public static String privateKeyToPem(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN RSA PRIVATE KEY-----\n" + base64 + "\n-----END RSA PRIVATE KEY-----";
    }

    /**
     * Parse a PEM-encoded public key string back to PublicKey object.
     *
     * @param pem the PEM-encoded public key
     * @return the parsed PublicKey
     */
    public static PublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(decoded);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse public key PEM", e);
        }
    }

    /**
     * Parse a PEM-encoded private key string back to PrivateKey object.
     *
     * @param pem the PEM-encoded private key
     * @return the parsed PrivateKey
     */
    public static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            return factory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse private key PEM", e);
        }
    }
}
