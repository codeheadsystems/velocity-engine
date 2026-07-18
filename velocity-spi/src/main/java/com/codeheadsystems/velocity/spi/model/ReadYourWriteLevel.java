// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * The read-your-write guarantee under which a value was produced (ADR 0007, §15 R2).
 *
 * <p>Read-your-write is a declared capability, not a universal guarantee. It rides on {@link
 * BackendCapabilities} as the backend's declared level and, because one {@code record()} can fan
 * out across features on different backends, also on every {@link FeatureValue} so each returned
 * value states the level under which <em>that</em> value was produced.
 */
public enum ReadYourWriteLevel {
  /**
   * The returned value reflects the caller's own write to every bucket it touched, atomically under
   * concurrency (NFR-6). Exact backends (Redis, JDBI/Postgres, DynamoDB) declare this.
   */
  ATOMIC,
  /**
   * A consistent snapshot that may not include the caller's just-applied write (e.g. a
   * consistent-but-lagging replica/materialized view). The backend must document its staleness
   * bound.
   */
  SNAPSHOT,
  /**
   * No read-your-write guarantee; the value may be stale/approximate and MUST be flagged {@link
   * Exactness#APPROXIMATE} (FR-7). Approximate backends (S3) declare this.
   */
  BESTEFFORT
}
