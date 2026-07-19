// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SLIDING_5S;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.countFeature;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The {@link com.codeheadsystems.velocity.spi.SlidingSupport} contract: a sliding window covers
 * {@code (now - duration, now]} against the backend clock (FR-3, ADR 0005). Events age out as the
 * clock advances past the duration, and the leading edge is exclusive at exactly the duration
 * boundary.
 */
public final class SlidingScenarios {

  private final CountStore store;
  private final MutableClock clock;

  /**
   * @param store a fresh count-capable, sliding backend under test
   * @param clock the backend's controllable clock
   */
  public SlidingScenarios(final CountStore store, final MutableClock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * An event stays in the window until the clock advances past the window duration, then drops out.
   */
  public void eventsAgeOutAsClockAdvances() {
    final Feature feature = countFeature(SLIDING_5S);
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));

    assertValue(currentCount(), 1);
    clock.advance(Duration.ofSeconds(3));
    assertValue(currentCount(), 1); // 3s < 5s, still inside
    clock.advance(Duration.ofSeconds(3)); // now +6s, past the 5s window
    assertValue(currentCount(), 0);
  }

  /** At exactly {@code now == eventTime + duration} the event is on the exclusive leading edge. */
  public void slidingLeadingEdgeIsExclusive() {
    final Feature feature = countFeature(SLIDING_5S);
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));

    assertValue(currentCount(), 1);
    clock.advance(
        Duration.ofSeconds(5)); // window is now (eventTime, eventTime+5s]; event sits at start
    assertValue(currentCount(), 0);
  }

  private com.codeheadsystems.velocity.spi.model.FeatureResult currentCount() {
    return store
        .queryCount(Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_5S)))
        .get(0);
  }
}
