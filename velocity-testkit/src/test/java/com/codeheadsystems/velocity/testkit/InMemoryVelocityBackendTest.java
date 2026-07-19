// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.BucketValue;
import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.spi.model.SeedAggregate;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Focused tests for backend logic beyond what the shared TCK scenarios exercise. */
class InMemoryVelocityBackendTest {

  private static final Instant FIXED = Instant.parse("2026-07-18T12:00:30Z");
  private static final Namespace NS = new Namespace("ns");
  private static final Subject SUBJECT = new Subject("card", "s1");
  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);
  private static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);
  private static final Window UNSUPPORTED = new Window(Duration.ofDays(7), WindowType.SLIDING);

  private MutableClock clock;
  private InMemoryVelocityBackend backend;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(FIXED);
    backend = new InMemoryVelocityBackend(clock);
  }

  @Test
  void noArgConstructorUsesSystemUtc() {
    assertThat(new InMemoryVelocityBackend().clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void capabilitiesDeclareTheReferenceProfile() {
    final BackendCapabilities caps = backend.capabilities();
    assertThat(caps.supportsAggregation(AggregationType.COUNT)).isTrue();
    assertThat(caps.supportsAggregation(AggregationType.SUM)).isTrue();
    assertThat(caps.supportsAggregation(AggregationType.DISTINCT)).isTrue();
    assertThat(caps.windows()).hasSize(4);
    assertThat(caps.distinctHllSliding()).isFalse();
    assertThat(caps.readYourWriteLevel()).isEqualTo(ReadYourWriteLevel.ATOMIC);
    assertThat(caps.idempotencySupported()).isFalse();
    assertThat(caps.seedSupported()).isTrue();
    assertThat(caps.maxAtomicFanOut()).isEqualTo(Integer.MAX_VALUE);
    assertThat(caps.maxRetention()).isEqualTo(Duration.ofDays(30));
    assertThat(caps.distinctExactCardinalityClamp()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void applyWithMixedWindowsFailsOnlyTheUnsupportedWindowButStillRecords() {
    final Feature feature =
        new Feature("mixed", Aggregation.count(), List.of(SLIDING_1M, UNSUPPORTED));
    final ApplyResult result =
        backend.applyCount(new ApplyContext(NS, null), List.of(new CountIntent(feature, SUBJECT)));

    assertThat(result.perFeature()).hasSize(2);
    final FeatureResult supported =
        result.perFeature().stream()
            .filter(pf -> pf.status() == ApplyStatus.APPLIED)
            .findFirst()
            .orElseThrow()
            .result();
    assertThat(((FeatureResult.Success) supported).value().value())
        .isEqualByComparingTo(BigDecimal.ONE);

    final FeatureResult failed =
        result.perFeature().stream()
            .filter(pf -> pf.status() == ApplyStatus.FAILED)
            .findFirst()
            .orElseThrow()
            .result();
    assertThat(((FeatureResult.Failure) failed).code()).isEqualTo(FailureCode.UNSUPPORTED_WINDOW);

    // The supported window still recorded the underlying event.
    assertThat(countOver(SLIDING_1M)).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void applyWithOnlyUnsupportedWindowsRecordsNothing() {
    final Feature feature = new Feature("only-bad", Aggregation.count(), List.of(UNSUPPORTED));
    final ApplyResult result =
        backend.applyCount(new ApplyContext(NS, null), List.of(new CountIntent(feature, SUBJECT)));

    assertThat(result.perFeature()).hasSize(1);
    assertThat(result.perFeature().get(0).status()).isEqualTo(ApplyStatus.FAILED);
    // Nothing was recorded, so a supported window still reads a known zero.
    assertThat(countOver(SLIDING_1M)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void queryUnsupportedWindowIsFailureNotSilentZero() {
    final FeatureResult result =
        backend
            .queryCount(
                new QueryContext(NS, null),
                List.of(new QueryTuple(SUBJECT, Aggregation.count(), UNSUPPORTED)))
            .get(0);
    assertThat(result.isSuccess()).isFalse();
    assertThat(((FeatureResult.Failure) result).code()).isEqualTo(FailureCode.UNSUPPORTED_WINDOW);
  }

  @Test
  void seedExactDistinctIsQueryable() {
    final Feature feature =
        new Feature("distinct", Aggregation.distinct("ip"), List.of(TUMBLING_1M));
    final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
    backend.seed(
        NS,
        SUBJECT,
        feature,
        List.of(
            new BucketValue(
                bucket,
                new SeedAggregate.ExactDistinct(List.of(member("a"), member("b"), member("a"))))));

    final FeatureResult result =
        backend
            .queryDistinct(
                new QueryContext(NS, null),
                List.of(new QueryTuple(SUBJECT, Aggregation.distinct("ip"), TUMBLING_1M)))
            .get(0);
    assertThat(((FeatureResult.Success) result).value().value())
        .isEqualByComparingTo(BigDecimal.valueOf(2));
  }

  @Test
  void seedSumBucketIsQueryable() {
    final Feature feature = new Feature("sum", Aggregation.sum(), List.of(TUMBLING_1M));
    final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
    backend.seed(
        NS,
        SUBJECT,
        feature,
        List.of(new BucketValue(bucket, new SeedAggregate.SumValue(BigDecimal.valueOf(750)))));

    final FeatureResult result =
        backend
            .querySum(
                new QueryContext(NS, null),
                List.of(new QueryTuple(SUBJECT, Aggregation.sum(), TUMBLING_1M)))
            .get(0);
    assertThat(((FeatureResult.Success) result).value().value())
        .isEqualByComparingTo(BigDecimal.valueOf(750));
  }

  @Test
  void purgeMissingScopeIsNoop() {
    assertThatCode(() -> backend.purge(NS, SUBJECT)).doesNotThrowAnyException();
    assertThatCode(() -> backend.purge(NS, null)).doesNotThrowAnyException();
  }

  private BigDecimal countOver(final Window window) {
    final FeatureResult result =
        backend
            .queryCount(
                new QueryContext(NS, null),
                List.of(new QueryTuple(SUBJECT, Aggregation.count(), window)))
            .get(0);
    return ((FeatureResult.Success) result).value().value();
  }

  private static DistinctMember member(final String token) {
    return new DistinctMember(token.getBytes(StandardCharsets.UTF_8));
  }
}
