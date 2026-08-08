# Sprint 8.5: Federation Protocol & HTTP Signatures

## Metadata
- **Sprint Number**: 8.5
- **Estimated Time**: 6-8 hours
- **Complexity**: High
- **Dependencies**: Sprint 7.5 (Consolidation)

## Objectives

1. Add battle-tested HTTP Signature library for S2S federation
2. Implement HTTP Signature signing (outbound) and verification (inbound)
3. Generate RSA key pairs for actors
4. Cache remote actor public keys
5. Enhance ActivityPubModels with missing fields

## Library Research Summary

### Adopt Immediately

| Library | Stars | License | Purpose |
|---------|-------|---------|---------|
| **tomitribe-http-signatures** | 93 | Apache 2.0 | HTTP Signature signing/verification |
| **jsonld-java** | 388 | BSD 3-Clause | JSON-LD @context processing |

### Why These Libraries

**tomitribe-http-signatures**:
- Battle-tested with Mastodon (draft-cavage-http-signatures)
- Simple `Signer`/`Verifier` API
- Framework-agnostic (works with Quarkus)
- Apache 2.0 license

**jsonld-java**:
- Only mature JSON-LD library in Java
- Needed for @context expansion if servers send complex contexts
- BSD 3-Clause license

### Keep Custom

| Component | Reason |
|-----------|--------|
| ActivityPubModels | Domain-specific, enhance with missing fields |
| WebFingerResource | Already correct (~30 lines, no library exists) |
| ActivityPubModelBuilder | Domain-specific mapping |
| InboxActivityProcessor | Business logic |

### Reference Implementations (Study Only)

| Project | Stars | Notes |
|---------|-------|-------|
| Smithereen | 547 | Java 21 server, study HTTP Signatures patterns |
| activityPub4j | 23 | Study AS2 model hierarchy |

### Libraries NOT Recommended

- **activitypub4j**: Unmaintained (2021), Spring Boot 2.x only
- **BigBone/Mastodon4J**: Client libraries, not server
- **Apache Commons RDF**: Overkill, inactive
- **Apache Clerezza**: OSGi-era, inactive

## What's Missing in Current Implementation

1. **HTTP Signature signing** (outbound) — Required for S2S federation
2. **HTTP Signature verification** (inbound) — Required to accept remote activities
3. **RSA key pair per actor** — For signing/verification
4. **Remote actor public key caching** — For verifying remote signatures

## Implementation Plan

### Phase 1: Add Dependencies (30 min)

1. Add `tomitribe-http-signatures` to pom.xml
2. Add `jsonld-java` to pom.xml (optional, for @context expansion)
3. Verify compilation

**pom.xml additions**:
```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>tomitribe-http-signatures</artifactId>
    <version>1.5</version>
</dependency>
```

### Phase 2: RSA Key Management (2 hours)

1. Add RSA key pair generation to Actor entity
2. Store public/private keys in database
3. Expose public key via ActivityPub actor endpoint

**New fields in Actor**:
```java
@Column(name = "public_key", columnDefinition = "TEXT")
public String publicKey;

@Column(name = "private_key", columnDefinition = "TEXT")
public String privateKey;
```

**New endpoint**:
```
GET /users/{username}/public-key
```

### Phase 3: Outbound Signing (2 hours)

1. Sign all outgoing activities in `FederationDeliveryService`
2. Add `Signature` and `Date` headers
3. Include `(request-target)`, `host`, `date` in signature

**Implementation**:
```java
Signer signer = new Signer(privateKey, keyId);
SignedRequest signed = signer.sign(request);
```

### Phase 4: Inbound Verification (2 hours)

1. Verify signatures on incoming activities in `InboxResource`
2. Fetch remote actor's public key (with caching)
3. Reject unsigned or invalid signatures

**Implementation**:
```java
Verifier verifier = new Verifier(publicKey);
boolean valid = verifier.verify(request);
```

### Phase 5: Key Caching (1 hour)

1. Cache remote actor public keys
2. Refresh cache periodically
3. Handle key rotation

**Storage**:
```java
@Entity
public class RemoteActorKey {
    public String actorUrl;
    public String publicKey;
    public LocalDateTime fetchedAt;
}
```

### Phase 6: Enhance ActivityPubModels (1 hour)

Add missing fields to match AS2 spec:

```java
public class Activity {
    // Existing fields...
    public String attributedTo;  // Actor URL
    public String to;            // Recipient
    public String cc;            // Carbon copy
    public String bcc;           // Blind carbon copy
    public String audience;      // Audience
    public String generator;     // Generator (e.g., "Open Pace")
    public String icon;          // Icon URL
    public String image;         // Image URL
    public String inReplyTo;     // Reply target
    public String contentMap;    // Localized content
    public String nameMap;       // Localized name
}
```

## Acceptance Criteria

### Dependencies
- [ ] `tomitribe-http-signatures` added and compiling
- [ ] `jsonld-java` added (optional)

### RSA Key Management
- [ ] Actor has RSA key pair on creation
- [ ] Public key accessible via endpoint
- [ ] Private key stored securely (encrypted at rest)

### Outbound Signing
- [ ] All federation deliveries signed
- [ ] Signature headers present (Signature, Date)
- [ ] Mastodon can verify our signatures

### Inbound Verification
- [ ] Unsigned requests rejected (401)
- [ ] Invalid signatures rejected (403)
- [ ] Remote actor public keys fetched and cached

### Key Caching
- [ ] Remote keys cached in database
- [ ] Cache refreshes periodically
- [ ] Key rotation handled gracefully

### ActivityPubModels
- [ ] All AS2 fields present
- [ ] Jackson annotations correct
- [ ] Backward compatible (existing fields unchanged)

## Testing Strategy

### Unit Tests
- Key generation tests
- Signature creation/verification tests
- Cache expiration tests

### Integration Tests
- Full federation flow with signed activities
- Mastodon interop test (manual)
- Reject unsigned requests

### Manual Testing
- Register user, verify key pair created
- Send activity to remote instance
- Receive activity from remote instance
- Verify signature validation

## Out of Scope

- UI/UX implementation (Sprint 9)
- Strava REST API compatibility (Sprint 10)
- Multiple instance support (Sprint 11)
- Advanced key rotation (Sprint 12)

## References

- [HTTP Signatures Spec](https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures)
- [ActivityPub HTTP Signatures](https://www.w3.org/TR/activitypub/#security-ld)
- [Mastodon HTTP Signatures](https://docs.joinmastodon.org/spec/security/#http-signatures)
- [tomitribe-http-signatures](https://github.com/apache/tomitribe-http-signatures)
- [jsonld-java](https://github.com/jsonld-java/jsonld-java)
