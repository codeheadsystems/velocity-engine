// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.tck.CapabilityConformanceScenarios;
import com.codeheadsystems.velocity.testkit.tck.CountStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.DistinctStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.PurgeScenarios;
import com.codeheadsystems.velocity.testkit.tck.SeedSupportScenarios;
import com.codeheadsystems.velocity.testkit.tck.SlidingScenarios;
import com.codeheadsystems.velocity.testkit.tck.SumStoreScenarios;
import com.codeheadsystems.velocity.testkit.tck.TumblingScenarios;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Drives every conformance {@code *Scenarios} suite against the {@link InMemoryVelocityBackend}, so
 * the reference backend passes its own TCK (ADR 0004). This is both the correctness proof for the
 * reference implementation and the coverage driver for the testkit's own gate.
 */
class InMemoryVelocityBackendTck {

  /** A deterministic, mid-minute/mid-hour instant so time-independent scenarios are stable. */
  private static final Instant FIXED = Instant.parse("2026-07-18T12:00:30Z");

  /**
   * A primary window and a distinct second window, both InMemory-supported, for the shared
   * scenarios.
   */
  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);

  private static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  private InMemoryVelocityBackend backend;
  private MutableClock clock;

  @BeforeEach
  void freshBackend() {
    clock = new MutableClock(FIXED);
    backend = new InMemoryVelocityBackend(clock);
  }

  @Nested
  class Count {
    @Test
    void applyThenQueryReturnsExactCount() {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H)
          .applyThenQueryReturnsExactCount();
    }

    @Test
    void applyResultReflectsWriteReadYourWrite() {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H)
          .applyResultReflectsWriteReadYourWrite();
    }

    @Test
    void applyEmitsOneResultPerWindow() {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H)
          .applyEmitsOneResultPerWindow();
    }

    @Test
    void valuesIsolatedByNamespace() {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H).valuesIsolatedByNamespace();
    }

    @Test
    void valuesIsolatedBySubject() {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H).valuesIsolatedBySubject();
    }

    @Test
    void concurrentApplyIsAtomic() throws Exception {
      new CountStoreScenarios(backend, clock, SLIDING_1M, TUMBLING_1H).concurrentApplyIsAtomic();
    }
  }

  @Nested
  class Sum {
    @Test
    void applyThenQueryReturnsExactSumCents() {
      new SumStoreScenarios(backend, clock, SLIDING_1M).applyThenQueryReturnsExactSumCents();
    }

    @Test
    void applyResultReflectsRunningSum() {
      new SumStoreScenarios(backend, clock, SLIDING_1M).applyResultReflectsRunningSum();
    }

    @Test
    void bigDecimalCentsPreservedWithoutOverflow() {
      new SumStoreScenarios(backend, clock, SLIDING_1M).bigDecimalCentsPreservedWithoutOverflow();
    }

    @Test
    void negativeValuesForRefunds() {
      new SumStoreScenarios(backend, clock, SLIDING_1M).negativeValuesForRefunds();
    }

    @Test
    void valuesIsolatedByNamespace() {
      new SumStoreScenarios(backend, clock, SLIDING_1M).valuesIsolatedByNamespace();
    }
  }

  @Nested
  class Distinct {
    @Test
    void applyThenQueryReturnsCardinality() {
      new DistinctStoreScenarios(backend, clock, SLIDING_1M).applyThenQueryReturnsCardinality();
    }

    @Test
    void deDupesRepeatedMembers() {
      new DistinctStoreScenarios(backend, clock, SLIDING_1M).deDupesRepeatedMembers();
    }

    @Test
    void applyResultReflectsCardinality() {
      new DistinctStoreScenarios(backend, clock, SLIDING_1M).applyResultReflectsCardinality();
    }

    @Test
    void valuesIsolatedBySubject() {
      new DistinctStoreScenarios(backend, clock, SLIDING_1M).valuesIsolatedBySubject();
    }
  }

  @Nested
  class Sliding {
    @Test
    void eventsAgeOutAsClockAdvances() {
      new SlidingScenarios(backend, clock).eventsAgeOutAsClockAdvances();
    }

    @Test
    void slidingLeadingEdgeIsExclusive() {
      new SlidingScenarios(backend, clock).slidingLeadingEdgeIsExclusive();
    }
  }

  @Nested
  class Tumbling {
    @Test
    void tumblingAccumulatesThenResetsAtBoundary() {
      new TumblingScenarios(backend, clock).tumblingAccumulatesThenResetsAtBoundary();
    }
  }

  @Nested
  class Seed {
    @Test
    void seededBucketMergesIdenticallyWithRecorded() {
      new SeedSupportScenarios<>(backend, clock).seededBucketMergesIdenticallyWithRecorded();
    }

    @Test
    void hllDistinctSeedRejected() {
      new SeedSupportScenarios<>(backend, clock).hllDistinctSeedRejected();
    }

    @Test
    void seedWithUnsupportedWindowRejected() {
      new SeedSupportScenarios<>(backend, clock).seedWithUnsupportedWindowRejected();
    }
  }

  @Nested
  class Capability {
    @Test
    void declaredAggregationsMatchImplementedMixins() {
      new CapabilityConformanceScenarios(backend).declaredAggregationsMatchImplementedMixins();
    }

    @Test
    void declaredWindowMarkersMatchWindowSpecs() {
      new CapabilityConformanceScenarios(backend).declaredWindowMarkersMatchWindowSpecs();
    }

    @Test
    void seedSupportFlagMatchesMixin() {
      new CapabilityConformanceScenarios(backend).seedSupportFlagMatchesMixin();
    }

    @Test
    void distinctHllSlidingIsFalse() {
      new CapabilityConformanceScenarios(backend).distinctHllSlidingIsFalse();
    }

    @Test
    void unsupportedWindowQueryIsDistinguishableFailure() {
      new CapabilityConformanceScenarios(backend).unsupportedWindowQueryIsDistinguishableFailure();
    }

    @Test
    void supportedEmptyWindowReturnsKnownZero() {
      new CapabilityConformanceScenarios(backend).supportedEmptyWindowReturnsKnownZero();
    }

    @Test
    void unsupportedWindowApplyIsDistinguishableFailure() {
      new CapabilityConformanceScenarios(backend).unsupportedWindowApplyIsDistinguishableFailure();
    }

    @Test
    void mixedApplyPartiallyFailsOnUnsupportedWindow() {
      new CapabilityConformanceScenarios(backend).mixedApplyPartiallyFailsOnUnsupportedWindow();
    }
  }

  @Nested
  class Purge {
    @Test
    void purgeSubjectClearsThatSubjectOnly() {
      new PurgeScenarios(backend, SLIDING_1M).purgeSubjectClearsThatSubjectOnly();
    }

    @Test
    void purgeNamespaceClearsEntireNamespace() {
      new PurgeScenarios(backend, SLIDING_1M).purgeNamespaceClearsEntireNamespace();
    }
  }
}
