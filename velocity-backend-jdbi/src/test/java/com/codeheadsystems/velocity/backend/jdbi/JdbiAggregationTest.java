// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BucketValue;
import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.spi.model.SeedAggregate;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.WindowMath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Backend-specific COUNT/SUM/DISTINCT/PURGE assertions against real Postgres, over TUMBLING windows
 * — the tumbling counterpart of the sliding-based {@code *StoreScenarios} the tumbling-only backend
 * cannot run. Same contract facets: exact windowed values, read-your-write, namespace/subject
 * isolation, seed merge, and the distinguishable-failure path for an unsupported window.
 */
final class JdbiAggregationTest extends AbstractJdbiBackendTest {

  private static final Namespace NS_A = new Namespace("jdbi-ns-a");
  private static final Namespace NS_B = new Namespace("jdbi-ns-b");
  private static final Subject SUBJECT_A = new Subject("card", "subject-a");
  private static final Subject SUBJECT_B = new Subject("card", "subject-b");
  private static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);
  private static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);
  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);
  private static final String DIMENSION = "ip";

  private static ApplyContext apply(final Namespace ns) {
    return new ApplyContext(ns, null);
  }

  private static QueryContext query(final Namespace ns) {
    return new QueryContext(ns, null);
  }

  private static DistinctMember member(final String token) {
    return new DistinctMember(token.getBytes(StandardCharsets.UTF_8));
  }

  private static void assertValue(final FeatureResult result, final long expected) {
    assertThat(result).isInstanceOf(FeatureResult.Success.class);
    final FeatureValue value = ((FeatureResult.Success) result).value();
    assertThat(value.value()).isEqualByComparingTo(BigDecimal.valueOf(expected));
  }

  private static FeatureValue successValue(final FeatureResult result) {
    assertThat(result).isInstanceOf(FeatureResult.Success.class);
    return ((FeatureResult.Success) result).value();
  }

  @Nested
  final class Count {

    private final Feature feature =
        new Feature("jdbi.count", Aggregation.count(), List.of(TUMBLING_1M));

    private FeatureResult read(final Namespace ns, final Subject subject) {
      return backend
          .queryCount(query(ns), List.of(new QueryTuple(subject, Aggregation.count(), TUMBLING_1M)))
          .get(0);
    }

    @Test
    void applyThenQueryReturnsExactCountFlaggedExactAtomic() {
      backend.applyCount(
          apply(NS_A),
          List.of(
              new CountIntent(feature, SUBJECT_A),
              new CountIntent(feature, SUBJECT_A),
              new CountIntent(feature, SUBJECT_A)));

      final FeatureResult result = read(NS_A, SUBJECT_A);
      assertValue(result, 3);
      assertThat(successValue(result).exactness()).isEqualTo(Exactness.EXACT);
      assertThat(successValue(result).readYourWriteLevel()).isEqualTo(ReadYourWriteLevel.ATOMIC);
      assertThat(successValue(result).asOf()).isEqualTo(clock.instant());
    }

    @Test
    void applyResultReflectsWriteReadYourWrite() {
      final ApplyResult first =
          backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
      assertThat(first.perFeature().get(0).status()).isEqualTo(ApplyStatus.APPLIED);
      assertValue(first.perFeature().get(0).result(), 1);

      final ApplyResult second =
          backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
      assertValue(second.perFeature().get(0).result(), 2);
    }

    @Test
    void multiWindowFeatureEmitsOneAppliedPerWindow() {
      final Feature multi =
          new Feature("jdbi.count.multi", Aggregation.count(), List.of(TUMBLING_1M, TUMBLING_1H));
      final ApplyResult result =
          backend.applyCount(apply(NS_A), List.of(new CountIntent(multi, SUBJECT_A)));

      assertThat(result.perFeature()).hasSize(2);
      assertThat(result.perFeature())
          .allSatisfy(pf -> assertThat(pf.status()).isEqualTo(ApplyStatus.APPLIED));
      assertThat(
              result.perFeature().stream().map(pf -> successValue(pf.result()).window()).toList())
          .containsExactlyInAnyOrder(TUMBLING_1M, TUMBLING_1H);
      assertThat(result.perFeature()).allSatisfy(pf -> assertValue(pf.result(), 1));
    }

    @Test
    void valuesIsolatedByNamespace() {
      backend.applyCount(
          apply(NS_A),
          List.of(new CountIntent(feature, SUBJECT_A), new CountIntent(feature, SUBJECT_A)));
      assertValue(read(NS_A, SUBJECT_A), 2);
      assertValue(read(NS_B, SUBJECT_A), 0);
    }

    @Test
    void valuesIsolatedBySubject() {
      backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
      assertValue(read(NS_A, SUBJECT_A), 1);
      assertValue(read(NS_A, SUBJECT_B), 0);
    }

    @Test
    void unsupportedWindowApplyIsDistinguishableFailureNotSilentZero() {
      final Feature sliding =
          new Feature("jdbi.count.sliding", Aggregation.count(), List.of(SLIDING_1M));
      final ApplyResult result =
          backend.applyCount(apply(NS_A), List.of(new CountIntent(sliding, SUBJECT_A)));
      assertThat(result.perFeature()).hasSize(1);
      assertThat(result.perFeature().get(0).status()).isEqualTo(ApplyStatus.FAILED);
      assertThat(result.perFeature().get(0).result()).isInstanceOf(FeatureResult.Failure.class);
      assertThat(((FeatureResult.Failure) result.perFeature().get(0).result()).code())
          .isEqualTo(FailureCode.UNSUPPORTED_WINDOW);
    }

    @Test
    void mixedFeatureAppliesSupportedWindowAndFailsUnsupportedInSameCall() {
      final Feature mixed =
          new Feature("jdbi.count.mixed", Aggregation.count(), List.of(TUMBLING_1M, SLIDING_1M));
      final ApplyResult result =
          backend.applyCount(apply(NS_A), List.of(new CountIntent(mixed, SUBJECT_A)));
      assertThat(result.perFeature()).hasSize(2);
      assertThat(result.perFeature().stream().map(pf -> pf.status()).toList())
          .containsExactlyInAnyOrder(ApplyStatus.APPLIED, ApplyStatus.FAILED);
      // The supported window still recorded exactly one event.
      assertValue(read(NS_A, SUBJECT_A), 1);
    }
  }

  @Nested
  final class Sum {

    private final Feature feature =
        new Feature("jdbi.sum", Aggregation.sum(), List.of(TUMBLING_1M));

    private FeatureValue read() {
      return successValue(
          backend
              .querySum(
                  query(NS_A), List.of(new QueryTuple(SUBJECT_A, Aggregation.sum(), TUMBLING_1M)))
              .get(0));
    }

    @Test
    void applyThenQueryReturnsExactSumCentsScaleZero() {
      backend.applySum(
          apply(NS_A),
          List.of(
              new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(150)),
              new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(250))));
      final FeatureValue value = read();
      assertThat(value.value()).isEqualByComparingTo(BigDecimal.valueOf(400));
      assertThat(value.value().scale()).isZero();
      assertThat(value.asOf()).isEqualTo(clock.instant());
    }

    @Test
    void applyResultReflectsRunningSum() {
      final ApplyResult first =
          backend.applySum(
              apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(500))));
      assertThat(successValue(first.perFeature().get(0).result()).value())
          .isEqualByComparingTo(BigDecimal.valueOf(500));
      final ApplyResult second =
          backend.applySum(
              apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(125))));
      assertThat(successValue(second.perFeature().get(0).result()).value())
          .isEqualByComparingTo(BigDecimal.valueOf(625));
    }

    @Test
    void bigDecimalCentsPreservedWithoutOverflow() {
      final BigDecimal big = new BigDecimal("9000000000000000000");
      backend.applySum(
          apply(NS_A),
          List.of(new SumIntent(feature, SUBJECT_A, big), new SumIntent(feature, SUBJECT_A, big)));
      assertThat(read().value()).isEqualByComparingTo(big.add(big));
    }

    @Test
    void negativeValuesForRefunds() {
      backend.applySum(
          apply(NS_A),
          List.of(
              new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(500)),
              new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(-200))));
      assertThat(read().value()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void emptyWindowIsKnownZero() {
      assertThat(read().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  final class Distinct {

    private final Feature feature =
        new Feature("jdbi.distinct", Aggregation.distinct(DIMENSION), List.of(TUMBLING_1M));

    private FeatureResult read(final Subject subject) {
      return backend
          .queryDistinct(
              query(NS_A),
              List.of(new QueryTuple(subject, Aggregation.distinct(DIMENSION), TUMBLING_1M)))
          .get(0);
    }

    @Test
    void applyThenQueryReturnsCardinalityFlaggedExact() {
      backend.applyDistinct(
          apply(NS_A),
          List.of(
              new DistinctIntent(feature, SUBJECT_A, member("alice")),
              new DistinctIntent(feature, SUBJECT_A, member("bob")),
              new DistinctIntent(feature, SUBJECT_A, member("carol"))));
      final FeatureResult result = read(SUBJECT_A);
      assertValue(result, 3);
      assertThat(successValue(result).exactness()).isEqualTo(Exactness.EXACT);
    }

    @Test
    void deDupesRepeatedMembers() {
      backend.applyDistinct(
          apply(NS_A),
          List.of(
              new DistinctIntent(feature, SUBJECT_A, member("alice")),
              new DistinctIntent(feature, SUBJECT_A, member("alice")),
              new DistinctIntent(feature, SUBJECT_A, member("bob"))));
      assertValue(read(SUBJECT_A), 2);
    }

    @Test
    void applyResultReflectsCardinalityReadYourWrite() {
      final ApplyResult first =
          backend.applyDistinct(
              apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("alice"))));
      assertValue(first.perFeature().get(0).result(), 1);
      final ApplyResult second =
          backend.applyDistinct(
              apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("bob"))));
      assertValue(second.perFeature().get(0).result(), 2);
    }

    @Test
    void valuesIsolatedBySubject() {
      backend.applyDistinct(
          apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("alice"))));
      assertValue(read(SUBJECT_B), 0);
    }
  }

  @Nested
  final class Purge {

    private final Feature feature =
        new Feature("jdbi.purge", Aggregation.count(), List.of(TUMBLING_1M));

    private FeatureResult count(final Namespace ns, final Subject subject) {
      return backend
          .queryCount(query(ns), List.of(new QueryTuple(subject, Aggregation.count(), TUMBLING_1M)))
          .get(0);
    }

    @Test
    void purgeSubjectClearsThatSubjectOnly() {
      backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
      backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_B)));

      backend.purge(NS_A, SUBJECT_A);

      assertValue(count(NS_A, SUBJECT_A), 0);
      assertValue(count(NS_A, SUBJECT_B), 1);
    }

    @Test
    void purgeNamespaceClearsEntireNamespace() {
      backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
      backend.applyCount(apply(NS_A), List.of(new CountIntent(feature, SUBJECT_B)));
      backend.applyCount(apply(NS_B), List.of(new CountIntent(feature, SUBJECT_A)));

      backend.purge(NS_A, null);

      assertValue(count(NS_A, SUBJECT_A), 0);
      assertValue(count(NS_A, SUBJECT_B), 0);
      assertValue(count(NS_B, SUBJECT_A), 1);
    }
  }

  @Nested
  final class Seed {

    @Test
    void seededSumBucketMergesWithRecorded() {
      final Feature feature = new Feature("jdbi.seed.sum", Aggregation.sum(), List.of(TUMBLING_1M));
      final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
      backend.seed(
          NS_A,
          SUBJECT_A,
          feature,
          List.of(new BucketValue(bucket, new SeedAggregate.SumValue(BigDecimal.valueOf(1000)))));
      backend.applySum(
          apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, BigDecimal.valueOf(250))));

      final FeatureValue value =
          successValue(
              backend
                  .querySum(
                      query(NS_A),
                      List.of(new QueryTuple(SUBJECT_A, Aggregation.sum(), TUMBLING_1M)))
                  .get(0));
      assertThat(value.value()).isEqualByComparingTo(BigDecimal.valueOf(1250));
    }

    @Test
    void seededExactDistinctBucketMergesWithRecorded() {
      final Feature feature =
          new Feature("jdbi.seed.distinct", Aggregation.distinct(DIMENSION), List.of(TUMBLING_1M));
      final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
      backend.seed(
          NS_A,
          SUBJECT_A,
          feature,
          List.of(
              new BucketValue(
                  bucket,
                  new SeedAggregate.ExactDistinct(List.of(member("alice"), member("bob"))))));
      // Recording an overlapping member ("bob") plus a new one ("carol") de-dupes against the seed.
      backend.applyDistinct(
          apply(NS_A),
          List.of(
              new DistinctIntent(feature, SUBJECT_A, member("bob")),
              new DistinctIntent(feature, SUBJECT_A, member("carol"))));

      assertValue(
          backend
              .queryDistinct(
                  query(NS_A),
                  List.of(new QueryTuple(SUBJECT_A, Aggregation.distinct(DIMENSION), TUMBLING_1M)))
              .get(0),
          3);
    }

    @Test
    void seedRejectsUnsupportedAggregationWindowSpan() {
      final Feature feature =
          new Feature("jdbi.seed.bad", Aggregation.count(), List.of(TUMBLING_1M));
      final WindowBounds start = WindowMath.tumblingBucket(clock.instant(), 60_000);
      final WindowBounds badBucket = new WindowBounds(start.start(), start.start().plusSeconds(90));
      assertThatThrownBy(
              () ->
                  backend.seed(
                      NS_A,
                      SUBJECT_A,
                      feature,
                      List.of(new BucketValue(badBucket, new SeedAggregate.CountValue(1)))))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void dataSourceConstructorProducesUsableBackend() {
    final JdbiVelocityBackend viaDs =
        new JdbiVelocityBackend(PostgresSupport.dataSource()).migrate();
    assertThat(viaDs.capabilities().seedSupported()).isTrue();
    assertThat(viaDs.clock()).isNotNull();
  }
}
