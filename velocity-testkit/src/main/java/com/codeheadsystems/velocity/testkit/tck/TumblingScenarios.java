// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.TUMBLING_1M;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.countFeature;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.testkit.MutableClock;
import com.codeheadsystems.velocity.testkit.WindowMath;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The {@link com.codeheadsystems.velocity.spi.TumblingSupport} contract: a tumbling window is the
 * aligned current bucket (FR-14). Events accumulate within a bucket and the value resets to zero
 * the instant the clock crosses into the next aligned bucket — edge-approximate at the boundary is
 * intended.
 */
public final class TumblingScenarios {

  private final CountStore store;
  private final MutableClock clock;

  /**
   * @param store a fresh count-capable, tumbling backend under test
   * @param clock the backend's controllable clock
   */
  public TumblingScenarios(final CountStore store, final MutableClock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Events accumulate inside a bucket, then the value resets when the clock crosses the boundary.
   */
  public void tumblingAccumulatesThenResetsAtBoundary() {
    final Instant bucketStart = WindowMath.tumblingBucket(clock.instant(), 60_000).start();
    clock.setInstant(bucketStart.plusSeconds(10)); // 10s into the current 1m bucket

    final Feature feature = countFeature(TUMBLING_1M);
    store.applyCount(
        Tck.apply(NS_A),
        List.of(new CountIntent(feature, SUBJECT_A), new CountIntent(feature, SUBJECT_A)));
    assertValue(currentCount(), 2);

    clock.setInstant(bucketStart.plusSeconds(65)); // 5s into the NEXT aligned bucket
    assertValue(currentCount(), 0);
  }

  private FeatureResult currentCount() {
    return store
        .queryCount(
            Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), TUMBLING_1M)))
        .get(0);
  }
}
