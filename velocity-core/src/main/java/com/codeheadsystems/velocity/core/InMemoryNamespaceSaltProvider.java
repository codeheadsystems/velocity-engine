// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.spi.model.Namespace;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A development/test {@link NamespaceSaltProvider} that derives a per-namespace salt from a single
 * in-process master secret.
 *
 * <p><strong>Not for production (§15 R11).</strong> The master secret is co-resident with the
 * process (and, in a test, with the distinct sets), which is exactly the custody posture R11
 * forbids. Production MUST use a KMS/secret-store-backed provider so the key lives in a separate
 * trust domain. The salt is {@code SHA-256(master || namespace)}, so it is deterministic per
 * namespace for a given master (reproducible hashing within a run) and distinct across namespaces.
 */
public final class InMemoryNamespaceSaltProvider implements NamespaceSaltProvider {

  private final byte[] master;
  private final ConcurrentMap<Namespace, byte[]> cache = new ConcurrentHashMap<>();

  /** Creates a provider with a random 32-byte master secret (distinct per instance). */
  public InMemoryNamespaceSaltProvider() {
    this(randomMaster());
  }

  /**
   * Creates a provider with an explicit master secret — use a fixed secret for reproducible hashing
   * across runs in tests.
   *
   * @param master the master secret bytes; copied defensively, must be non-empty
   */
  public InMemoryNamespaceSaltProvider(final byte[] master) {
    Objects.requireNonNull(master, "master");
    if (master.length == 0) {
      throw new IllegalArgumentException("master secret must not be empty");
    }
    this.master = master.clone();
  }

  @Override
  public byte[] salt(final Namespace namespace) {
    Objects.requireNonNull(namespace, "namespace");
    return cache.computeIfAbsent(namespace, this::derive).clone();
  }

  private byte[] derive(final Namespace namespace) {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
    digest.update(master);
    digest.update(namespace.value().getBytes(StandardCharsets.UTF_8));
    return digest.digest();
  }

  private static byte[] randomMaster() {
    final byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }
}
