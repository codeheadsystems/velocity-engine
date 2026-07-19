// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.tck.CapabilityConformanceScenarios;
import com.codeheadsystems.velocity.testkit.tck.CountStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.DistinctStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.PurgeScenarios;
import com.codeheadsystems.velocity.testkit.tck.SeedSupportScenarios;
import com.codeheadsystems.velocity.testkit.tck.SumStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.TumblingScenarios;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Drives the full {@code velocity-testkit} conformance TCK against real Postgres for every scenario
 * that applies to this tumbling-only backend (ADR 0004) — the same shared {@code *Scenarios} the
 * reference in-memory backend runs, now parameterized by window so a tumbling backend supplies its
 * own supported windows. This proves the JDBI backend honors the identical frozen contract.
 *
 * <p>{@code SlidingScenarios} is intentionally NOT run — this backend declares no sliding windows.
 * The window-agnostic aggregation suites ({@code Count/Sum/Distinct/PurgeStoreScenarios}) run here
 * with {@link WindowType#TUMBLING tumbling} windows, including the shared concurrency scenario
 * against real Postgres (acceptance #1).
 */
final class JdbiConformanceTckTest extends AbstractJdbiBackendTest {

  private static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);
  private static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  private CountStoreScenarios count() {
    return new CountStoreScenarios(backend, clock, TUMBLING_1M, TUMBLING_1H);
  }

  private SumStoreScenarios sum() {
    return new SumStoreScenarios(backend, clock, TUMBLING_1H);
  }

  private DistinctStoreScenarios distinct() {
    return new DistinctStoreScenarios(backend, clock, TUMBLING_1H);
  }

  private PurgeScenarios purge() {
    return new PurgeScenarios(backend, TUMBLING_1H);
  }

  private TumblingScenarios tumbling() {
    return new TumblingScenarios(backend, clock);
  }

  private SeedSupportScenarios<JdbiVelocityBackend> seed() {
    return new SeedSupportScenarios<>(backend, clock);
  }

  private CapabilityConformanceScenarios capability() {
    return new CapabilityConformanceScenarios(backend);
  }

  // ---- CountStoreScenarios (tumbling) ----

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

  /** The shared concurrency scenario (acceptance #1) against real Postgres. */
  @Test
  void concurrentApplyIsAtomic() throws Exception {
    count().concurrentApplyIsAtomic();
  }

  // ---- SumStoreScenarios (tumbling) ----

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

  // ---- DistinctStoreScenarios (tumbling) ----

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

  // ---- PurgeScenarios (tumbling) ----

  @Test
  void purgeSubjectClearsThatSubjectOnly() {
    purge().purgeSubjectClearsThatSubjectOnly();
  }

  @Test
  void purgeNamespaceClearsEntireNamespace() {
    purge().purgeNamespaceClearsEntireNamespace();
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

  @Test
  void unsupportedWindowApplyIsDistinguishableFailure() {
    capability().unsupportedWindowApplyIsDistinguishableFailure();
  }

  @Test
  void mixedApplyPartiallyFailsOnUnsupportedWindow() {
    capability().mixedApplyPartiallyFailsOnUnsupportedWindow();
  }
}
