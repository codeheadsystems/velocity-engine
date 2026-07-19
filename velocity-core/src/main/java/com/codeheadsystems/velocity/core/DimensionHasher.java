// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.Namespace;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hashes a raw DISTINCT dimension value into an opaque, fixed-width {@link DistinctMember} so raw
 * PII (IPs, tokens, device IDs) never reaches a backend (FR-38, §15 R11).
 *
 * <p>The token is {@code HMAC-SHA256(salt, value)} — 32 bytes — with the per-namespace salt drawn
 * from a {@link NamespaceSaltProvider}. The mapping is deterministic per {@code (namespace, value)}
 * (so the same value counts once) and different values produce different members (so cardinality is
 * preserved), while the backend only ever stores and counts the opaque token. Keyed hashing (not a
 * bare digest) is what makes a low-entropy dimension resistant to offline brute-force <em>when the
 * salt lives in a separate trust domain</em> (§15 R11); see {@link NamespaceSaltProvider}.
 */
public final class DimensionHasher {

  private static final String HMAC_SHA256 = "HmacSHA256";

  private final NamespaceSaltProvider saltProvider;

  /**
   * Creates a hasher backed by the given salt provider.
   *
   * @param saltProvider the per-namespace salt source
   */
  public DimensionHasher(final NamespaceSaltProvider saltProvider) {
    this.saltProvider = Objects.requireNonNull(saltProvider, "saltProvider");
  }

  /**
   * Hashes a raw dimension value into an opaque distinct member.
   *
   * @param namespace the namespace (selects the salt)
   * @param value the raw dimension value
   * @return the 32-byte keyed-hash member
   */
  public DistinctMember hash(final Namespace namespace, final String value) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(value, "value");
    final Mac mac = newMac(saltProvider.salt(namespace));
    final byte[] token = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    return new DistinctMember(token);
  }

  private static Mac newMac(final byte[] salt) {
    try {
      final Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(salt, HMAC_SHA256));
      return mac;
    } catch (final java.security.NoSuchAlgorithmException e) {
      // HmacSHA256 is a mandated JCA algorithm; its absence is an unrecoverable environment fault.
      throw new IllegalStateException("HmacSHA256 not available", e);
    } catch (final InvalidKeyException e) {
      throw new IllegalStateException("invalid HMAC salt", e);
    }
  }
}
