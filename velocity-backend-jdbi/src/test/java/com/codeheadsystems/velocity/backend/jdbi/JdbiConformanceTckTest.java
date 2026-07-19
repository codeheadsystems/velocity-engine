// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import com.codeheadsystems.velocity.testkit.tck.CapabilityConformanceScenarios;
import com.codeheadsystems.velocity.testkit.tck.SeedSupportScenarios;
import com.codeheadsystems.velocity.testkit.tck.TumblingScenarios;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@code velocity-testkit} conformance TCK against real Postgres for the scenarios that
 * apply to a tumbling-only backend (ADR 0004): {@link TumblingScenarios}, {@link
 * SeedSupportScenarios} and {@link CapabilityConformanceScenarios}. This proves the JDBI backend
 * honors the same frozen contract as the reference in-memory backend.
 *
 * <p>{@code SlidingScenarios} is intentionally NOT run — this backend declares no sliding windows.
 * The aggregation suites ({@code Count/Sum/Distinct/PurgeStoreScenarios}) hardcode a sliding window
 * in their fixtures, so they cannot run against a tumbling-only backend; the equivalent tumbling
 * assertions live in {@link JdbiAggregationTest}.
 */
final class JdbiConformanceTckTest extends AbstractJdbiBackendTest {

  private TumblingScenarios tumbling() {
    return new TumblingScenarios(backend, clock);
  }

  private SeedSupportScenarios<JdbiVelocityBackend> seed() {
    return new SeedSupportScenarios<>(backend, clock);
  }

  private CapabilityConformanceScenarios capability() {
    return new CapabilityConformanceScenarios(backend);
  }

  // ---- TumblingScenarios ----

  @Test
  void tumblingAccumulatesThenResetsAtBoundary() {
    tumbling().tumblingAccumulatesThenResetsAtBoundary();
  }

  // ---- SeedSupportScenarios (acceptance #16) ----

  @Test
  void seededBucketMergesIdenticallyWithRecorded() {
    seed().seededBucketMergesIdenticallyWithRecorded();
  }

  @Test
  void hllDistinctSeedRejected() {
    seed().hllDistinctSeedRejected();
  }

  @Test
  void seedWithUnsupportedWindowRejected() {
    seed().seedWithUnsupportedWindowRejected();
  }

  // ---- CapabilityConformanceScenarios (ADR 0004) ----

  @Test
  void declaredAggregationsMatchImplementedMixins() {
    capability().declaredAggregationsMatchImplementedMixins();
  }

  @Test
  void declaredWindowMarkersMatchWindowSpecs() {
    capability().declaredWindowMarkersMatchWindowSpecs();
  }

  @Test
  void seedSupportFlagMatchesMixin() {
    capability().seedSupportFlagMatchesMixin();
  }

  @Test
  void distinctHllSlidingIsFalse() {
    capability().distinctHllSlidingIsFalse();
  }

  @Test
  void unsupportedWindowQueryIsDistinguishableFailure() {
    capability().unsupportedWindowQueryIsDistinguishableFailure();
  }

  @Test
  void supportedEmptyWindowReturnsKnownZero() {
    capability().supportedEmptyWindowReturnsKnownZero();
  }
}
