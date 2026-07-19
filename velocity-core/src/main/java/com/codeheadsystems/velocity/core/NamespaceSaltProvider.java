// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.spi.model.Namespace;

/**
 * Supplies the per-namespace HMAC salt used to keyed-hash DISTINCT dimension values at rest (FR-38,
 * §15 R11).
 *
 * <p><strong>Key custody (§15 R11).</strong> In production the salt/key MUST live in a <em>separate
 * trust domain</em> (a KMS or secret store), <em>not co-resident</em> with the distinct sets: a
 * low-entropy dimension like an IPv4 (2³² space) is brute-forceable offline after a single
 * datastore dump if the salt is dumped alongside it, defeating the "no raw PII at rest" guarantee.
 * A KMS-backed implementation is a deferred follow-up; {@link InMemoryNamespaceSaltProvider} is a
 * development/test default only.
 */
public interface NamespaceSaltProvider {

  /**
   * The salt bytes for a namespace.
   *
   * @param namespace the namespace
   * @return the salt; must be stable per namespace and never empty
   */
  byte[] salt(Namespace namespace);
}
