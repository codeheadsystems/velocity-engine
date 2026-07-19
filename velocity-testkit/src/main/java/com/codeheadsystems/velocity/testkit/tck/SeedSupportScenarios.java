// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.TUMBLING_1M;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.countFeature;
import static com.codeheadsystems.velocity.testkit.tck.Tck.distinctFeature;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.SeedSupport;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.BucketValue;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.SeedAggregate;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.testkit.MutableClock;
import com.codeheadsystems.velocity.testkit.WindowMath;
import java.util.List;
import java.util.Objects;

/**
 * The {@link SeedSupport} contract (ADR 0008): a seeded per-bucket aggregate and an organically
 * recorded one merge identically through the same windowed read path (acceptance #16), an
 * unrepresentable seed is rejected, and — by type — a single total can never be seeded (a {@link
 * BucketValue} always carries {@link WindowBounds}, so there is no total-only variant to pass).
 *
 * @param <B> a backend that both records/queries counts and supports seeding
 */
public final class SeedSupportScenarios<B extends CountStore & SeedSupport> {

  private final B backend;
  private final MutableClock clock;

  /**
   * @param backend a fresh seed-capable, count-capable backend under test
   * @param clock the backend's controllable clock
   */
  public SeedSupportScenarios(final B backend, final MutableClock clock) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * A bucket seeded with {@code CountValue(n)} answers a windowed query exactly as {@code n} events
   * recorded organically into that same bucket do — the seed/record equivalence of ADR 0008.
   */
  public void seededBucketMergesIdenticallyWithRecorded() {
    final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
    final Feature feature = countFeature(TUMBLING_1M);

    // Seeded path: subject A gets a pre-computed bucket count of 3.
    backend.seed(
        NS_A,
        SUBJECT_A,
        feature,
        List.of(new BucketValue(bucket, new SeedAggregate.CountValue(3))));

    // Recorded path: subject B gets 3 organically recorded events in the same current bucket.
    backend.applyCount(
        Tck.apply(NS_A),
        List.of(
            new CountIntent(feature, SUBJECT_B),
            new CountIntent(feature, SUBJECT_B),
            new CountIntent(feature, SUBJECT_B)));

    assertValue(query(SUBJECT_A), 3);
    assertValue(query(SUBJECT_B), 3);
  }

  /** An exact-only backend rejects an HLL-sketch distinct seed (ADR 0005/0006). */
  public void hllDistinctSeedRejected() {
    final WindowBounds bucket = WindowMath.tumblingBucket(clock.instant(), 60_000);
    final Feature feature = distinctFeature(TUMBLING_1M);
    assertThatThrownBy(
            () ->
                backend.seed(
                    NS_A,
                    SUBJECT_A,
                    feature,
                    List.of(
                        new BucketValue(
                            bucket, new SeedAggregate.HllDistinct(new byte[] {1, 2, 3, 4})))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A bucket whose span matches no supported window of the feature is rejected. */
  public void seedWithUnsupportedWindowRejected() {
    final WindowBounds start = WindowMath.tumblingBucket(clock.instant(), 60_000);
    // A 90-second bucket: the feature declares only a 1-minute window, so nothing matches.
    final WindowBounds badBucket = new WindowBounds(start.start(), start.start().plusSeconds(90));
    final Feature feature = countFeature(TUMBLING_1M);
    assertThatThrownBy(
            () ->
                backend.seed(
                    NS_A,
                    SUBJECT_A,
                    feature,
                    List.of(new BucketValue(badBucket, new SeedAggregate.CountValue(1)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private com.codeheadsystems.velocity.spi.model.FeatureResult query(
      final com.codeheadsystems.velocity.spi.model.Subject subject) {
    return backend
        .queryCount(Tck.query(NS_A), List.of(Tck.tuple(subject, Aggregation.count(), TUMBLING_1M)))
        .get(0);
  }
}
