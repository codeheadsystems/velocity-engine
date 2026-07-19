// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.DIMENSION;
import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.UNSUPPORTED;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertFailure;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.SeedSupport;
import com.codeheadsystems.velocity.spi.SlidingSupport;
import com.codeheadsystems.velocity.spi.SumStore;
import com.codeheadsystems.velocity.spi.TumblingSupport;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.util.List;
import java.util.Objects;

/**
 * The ADR 0004 coherence suite: what a backend <em>declares</em> in {@link BackendCapabilities}
 * must match what it <em>implements</em>, and the negatives are contract.
 * Declaration↔implementation agreement, {@code distinctHllSliding == false}, an unsupported window
 * that fast-rejects with a distinguishable {@link FailureCode#UNSUPPORTED_WINDOW} (never a silent
 * {@code 0}), and a supported empty window that returns a known {@code Success(0)} rather than a
 * failure.
 */
public final class CapabilityConformanceScenarios {

  private final VelocityBackend backend;

  /**
   * @param backend the backend under test
   */
  public CapabilityConformanceScenarios(final VelocityBackend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /** Each declared aggregation is implemented as its mix-in, and each undeclared one is not. */
  public void declaredAggregationsMatchImplementedMixins() {
    final BackendCapabilities caps = backend.capabilities();
    assertThat(caps.supportsAggregation(AggregationType.COUNT))
        .isEqualTo(backend instanceof CountStore);
    assertThat(caps.supportsAggregation(AggregationType.SUM))
        .isEqualTo(backend instanceof SumStore);
    assertThat(caps.supportsAggregation(AggregationType.DISTINCT))
        .isEqualTo(backend instanceof DistinctStore);
  }

  /** The window markers agree with the declared window specs' types. */
  public void declaredWindowMarkersMatchWindowSpecs() {
    final BackendCapabilities caps = backend.capabilities();
    final boolean anySliding =
        caps.windows().stream().anyMatch(spec -> spec.window().type() == WindowType.SLIDING);
    final boolean anyTumbling =
        caps.windows().stream().anyMatch(spec -> spec.window().type() == WindowType.TUMBLING);
    assertThat(backend instanceof SlidingSupport).isEqualTo(anySliding);
    assertThat(backend instanceof TumblingSupport).isEqualTo(anyTumbling);
  }

  /** The seed capability flag agrees with the presence of the {@link SeedSupport} mix-in. */
  public void seedSupportFlagMatchesMixin() {
    assertThat(backend.capabilities().seedSupported()).isEqualTo(backend instanceof SeedSupport);
  }

  /**
   * HLL-distinct on a sliding window is structurally impossible: the flag must be false (ADR 0005).
   */
  public void distinctHllSlidingIsFalse() {
    assertThat(backend.capabilities().distinctHllSliding()).isFalse();
  }

  /**
   * Querying an unsupported window fast-rejects with a distinguishable {@link
   * FailureCode#UNSUPPORTED_WINDOW} — never a silent {@code 0} (ADR 0009 rule 1, FR-13).
   */
  public void unsupportedWindowQueryIsDistinguishableFailure() {
    final FeatureResult result = queryOne(UNSUPPORTED);
    assertThat(result.isSuccess()).isFalse();
    assertFailure(result, FailureCode.UNSUPPORTED_WINDOW);
  }

  /**
   * A supported window with no data returns a known {@code Success(0)}, not a failure — the backend
   * knows the answer is zero (distinct from "I could not answer").
   */
  public void supportedEmptyWindowReturnsKnownZero() {
    final Window supported = backend.capabilities().windows().get(0).window();
    assertValue(queryOne(supported), 0);
  }

  private FeatureResult queryOne(final Window window) {
    final QueryContext ctx = Tck.query(NS_A);
    if (backend instanceof CountStore countStore) {
      return countStore
          .queryCount(ctx, List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), window)))
          .get(0);
    }
    if (backend instanceof SumStore sumStore) {
      return sumStore
          .querySum(ctx, List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
          .get(0);
    }
    if (backend instanceof DistinctStore distinctStore) {
      return distinctStore
          .queryDistinct(
              ctx, List.of(Tck.tuple(SUBJECT_A, Aggregation.distinct(DIMENSION), window)))
          .get(0);
    }
    throw new IllegalStateException("backend implements no aggregation store to query");
  }
}
