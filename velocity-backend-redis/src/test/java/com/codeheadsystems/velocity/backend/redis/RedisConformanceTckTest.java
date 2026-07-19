// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.redis;

import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.tck.CapabilityConformanceScenarios;
import com.codeheadsystems.velocity.testkit.tck.CountStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.DistinctStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.PurgeScenarios;
import com.codeheadsystems.velocity.testkit.tck.SlidingScenarios;
import com.codeheadsystems.velocity.testkit.tck.SumStoreScenarios;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Drives the shared {@code velocity-testkit} conformance TCK against real Redis for every scenario
 * that applies to this sliding backend (ADR 0004) — the same window-parameterized {@code
 * *Scenarios} the reference in-memory backend and the JDBI backend run, here with SLIDING windows,
 * plus {@link SlidingScenarios} (aging / exclusive leading edge).
 *
 * <p>{@code TumblingScenarios} is intentionally NOT run — this backend declares no tumbling
 * windows. {@code SeedSupportScenarios} is not run either — {@link RedisVelocityBackend} does not
 * implement the optional {@code SeedSupport} mix-in in v1 (ADR 0008's per-bucket seed is a tumbling
 * concept).
 */
final class RedisConformanceTckTest extends AbstractRedisBackendTest {

  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);
  private static final Window SLIDING_30S = new Window(Duration.ofSeconds(30), WindowType.SLIDING);

  private CountStoreScenarios count() {
    return new CountStoreScenarios(backend, clock, SLIDING_1M, SLIDING_30S);
  }

  private SumStoreScenarios sum() {
    return new SumStoreScenarios(backend, clock, SLIDING_1M);
  }

  private DistinctStoreScenarios distinct() {
    return new DistinctStoreScenarios(backend, clock, SLIDING_1M);
  }

  private PurgeScenarios purge() {
    return new PurgeScenarios(backend, SLIDING_1M);
  }

  private SlidingScenarios sliding() {
    return new SlidingScenarios(backend, clock);
  }

  private CapabilityConformanceScenarios capability() {
    return new CapabilityConformanceScenarios(backend);
  }

  // ---- CountStoreScenarios (sliding) ----

  @Test
  void applyThenQueryReturnsExactCount() {
    count().applyThenQueryReturnsExactCount();
  }

  @Test
  void applyResultReflectsWriteReadYourWrite() {
    count().applyResultReflectsWriteReadYourWrite();
  }

  @Test
  void applyEmitsOneResultPerWindow() {
    count().applyEmitsOneResultPerWindow();
  }

  @Test
  void countValuesIsolatedByNamespace() {
    count().valuesIsolatedByNamespace();
  }

  @Test
  void countValuesIsolatedBySubject() {
    count().valuesIsolatedBySubject();
  }

  /** The shared concurrency scenario (acceptance #2) against real Redis. */
  @Test
  void concurrentApplyIsAtomic() throws Exception {
    count().concurrentApplyIsAtomic();
  }

  // ---- SumStoreScenarios (sliding) ----

  @Test
  void applyThenQueryReturnsExactSumCents() {
    sum().applyThenQueryReturnsExactSumCents();
  }

  @Test
  void applyResultReflectsRunningSum() {
    sum().applyResultReflectsRunningSum();
  }

  @Test
  void bigDecimalCentsPreservedWithoutOverflow() {
    sum().bigDecimalCentsPreservedWithoutOverflow();
  }

  @Test
  void negativeValuesForRefunds() {
    sum().negativeValuesForRefunds();
  }

  @Test
  void sumValuesIsolatedByNamespace() {
    sum().valuesIsolatedByNamespace();
  }

  // ---- DistinctStoreScenarios (sliding) ----

  @Test
  void applyThenQueryReturnsCardinality() {
    distinct().applyThenQueryReturnsCardinality();
  }

  @Test
  void deDupesRepeatedMembers() {
    distinct().deDupesRepeatedMembers();
  }

  @Test
  void applyResultReflectsCardinality() {
    distinct().applyResultReflectsCardinality();
  }

  @Test
  void distinctValuesIsolatedBySubject() {
    distinct().valuesIsolatedBySubject();
  }

  // ---- PurgeScenarios (sliding) ----

  @Test
  void purgeSubjectClearsThatSubjectOnly() {
    purge().purgeSubjectClearsThatSubjectOnly();
  }

  @Test
  void purgeNamespaceClearsEntireNamespace() {
    purge().purgeNamespaceClearsEntireNamespace();
  }

  // ---- SlidingScenarios (aging / exclusive leading edge) ----

  @Test
  void eventsAgeOutAsClockAdvances() {
    sliding().eventsAgeOutAsClockAdvances();
  }

  @Test
  void slidingLeadingEdgeIsExclusive() {
    sliding().slidingLeadingEdgeIsExclusive();
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

  @Test
  void unsupportedWindowApplyIsDistinguishableFailure() {
    capability().unsupportedWindowApplyIsDistinguishableFailure();
  }

  @Test
  void mixedApplyPartiallyFailsOnUnsupportedWindow() {
    capability().mixedApplyPartiallyFailsOnUnsupportedWindow();
  }
}
