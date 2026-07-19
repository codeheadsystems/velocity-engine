// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.cents;
import static com.codeheadsystems.velocity.testkit.tck.Tck.successValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.sumFeature;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.SumStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The {@link SumStore} contract: sums are exact {@code BigDecimal} cents (P3/FR-10) — no binary
 * float, no silent overflow — negatives are honored for refunds, and values are namespace-isolated.
 */
public final class SumStoreScenarios {

  private final SumStore store;
  private final MutableClock clock;
  private final Window window;

  /**
   * @param store a fresh sum-capable backend under test
   * @param clock the backend's controllable clock
   * @param window a window the backend supports, exercised by these scenarios
   */
  public SumStoreScenarios(final SumStore store, final MutableClock clock, final Window window) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.window = Objects.requireNonNull(window, "window");
  }

  /** Apply cents values then query returns their exact sum with scale 0. */
  public void applyThenQueryReturnsExactSumCents() {
    final Feature feature = sumFeature(window);
    store.applySum(
        Tck.apply(NS_A),
        List.of(
            new SumIntent(feature, SUBJECT_A, cents(150)),
            new SumIntent(feature, SUBJECT_A, cents(250))));

    final BigDecimal value =
        successValue(
                store
                    .querySum(
                        Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
                    .get(0))
            .value();
    assertThat(value).isEqualByComparingTo(cents(400));
    assertThat(value.scale()).isZero();
    assertThat(
            successValue(
                    store
                        .querySum(
                            Tck.query(NS_A),
                            List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
                        .get(0))
                .asOf())
        .isEqualTo(clock.instant());
  }

  /** The value returned by apply already reflects the running sum (read-your-write). */
  public void applyResultReflectsRunningSum() {
    final Feature feature = sumFeature(window);
    final ApplyResult first =
        store.applySum(Tck.apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, cents(500))));
    assertThat(successValue(first.perFeature().get(0).result()).value())
        .isEqualByComparingTo(cents(500));

    final ApplyResult second =
        store.applySum(Tck.apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, cents(125))));
    assertThat(successValue(second.perFeature().get(0).result()).value())
        .isEqualByComparingTo(cents(625));
  }

  /**
   * Large cents sums are preserved exactly — well past a {@code long}-cents / double's safe range.
   */
  public void bigDecimalCentsPreservedWithoutOverflow() {
    final Feature feature = sumFeature(window);
    final BigDecimal big = new BigDecimal("9000000000000000000"); // > Long.MAX_VALUE / 2
    store.applySum(
        Tck.apply(NS_A),
        List.of(new SumIntent(feature, SUBJECT_A, big), new SumIntent(feature, SUBJECT_A, big)));

    final BigDecimal value =
        successValue(
                store
                    .querySum(
                        Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
                    .get(0))
            .value();
    assertThat(value).isEqualByComparingTo(big.add(big));
  }

  /** A negative value (refund) reduces the sum. */
  public void negativeValuesForRefunds() {
    final Feature feature = sumFeature(window);
    store.applySum(
        Tck.apply(NS_A),
        List.of(
            new SumIntent(feature, SUBJECT_A, cents(500)),
            new SumIntent(feature, SUBJECT_A, cents(-200))));

    assertThat(
            successValue(
                    store
                        .querySum(
                            Tck.query(NS_A),
                            List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
                        .get(0))
                .value())
        .isEqualByComparingTo(cents(300));
  }

  /** Sums in one namespace are invisible in another; the other reads a known zero. */
  public void valuesIsolatedByNamespace() {
    final Feature feature = sumFeature(window);
    store.applySum(Tck.apply(NS_A), List.of(new SumIntent(feature, SUBJECT_A, cents(999))));

    assertThat(
            successValue(
                    store
                        .querySum(
                            Tck.query(NS_B),
                            List.of(Tck.tuple(SUBJECT_A, Aggregation.sum(), window)))
                        .get(0))
                .value())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }
}
