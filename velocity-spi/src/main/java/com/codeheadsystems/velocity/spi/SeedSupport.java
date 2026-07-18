// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

import com.codeheadsystems.velocity.spi.model.BucketValue;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Subject;
import java.util.List;

/**
 * Optional mix-in: seed pre-computed historical aggregates (FR-32, ADR 0008).
 *
 * <p>Seeding places per-bucket aggregate values into the same buckets a windowed query later merges
 * (FR-14) — a single total is explicitly not a valid seed, since it cannot be apportioned across a
 * window's buckets (ADR 0008). Seeded state is flagged onboarding-seeded, distinct from organically
 * recorded state.
 *
 * <p>This mix-in is <strong>optional</strong>: a backend that cannot represent seeded buckets
 * simply does not implement it and declares {@code seedSupported == false}. A backend that does
 * implement it MUST reject a seed it cannot store — in particular an HLL sketch it did not itself
 * produce (opaque, same-implementation-only bytes, ADR 0006) or an HLL sketch on a sliding feature
 * (ADR 0005). Seed is an admin operation, rate-isolated from the hot path (FR-33).
 */
public interface SeedSupport extends VelocityBackend {

  /**
   * Seeds pre-computed per-bucket aggregates for a {@code (namespace, subject, feature)}.
   *
   * @param namespace the namespace to seed within
   * @param subject the subject the aggregates are attributed to
   * @param feature the feature being seeded
   * @param buckets the per-bucket aggregate values to place
   */
  void seed(Namespace namespace, Subject subject, Feature feature, List<BucketValue> buckets);
}
