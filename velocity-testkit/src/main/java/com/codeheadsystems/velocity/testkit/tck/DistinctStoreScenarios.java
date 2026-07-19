// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.DIMENSION;
import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.distinctFeature;
import static com.codeheadsystems.velocity.testkit.tck.Tck.member;
import static com.codeheadsystems.velocity.testkit.tck.Tck.successValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.util.List;
import java.util.Objects;

/**
 * The {@link DistinctStore} contract: cardinality counts distinct members, repeated members
 * de-dupe, values are subject-isolated, and this exact backend flags every value {@link
 * Exactness#EXACT}.
 */
public final class DistinctStoreScenarios {

  private final DistinctStore store;
  private final MutableClock clock;
  private final Window window;

  /**
   * @param store a fresh distinct-capable backend under test
   * @param clock the backend's controllable clock
   * @param window a window the backend supports, exercised by these scenarios
   */
  public DistinctStoreScenarios(
      final DistinctStore store, final MutableClock clock, final Window window) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.window = Objects.requireNonNull(window, "window");
  }

  /** Apply distinct members then query returns the exact cardinality of the distinct set. */
  public void applyThenQueryReturnsCardinality() {
    final Feature feature = distinctFeature(window);
    store.applyDistinct(
        Tck.apply(NS_A),
        List.of(
            new DistinctIntent(feature, SUBJECT_A, member("alice")),
            new DistinctIntent(feature, SUBJECT_A, member("bob")),
            new DistinctIntent(feature, SUBJECT_A, member("carol"))));

    final var result =
        store
            .queryDistinct(
                Tck.query(NS_A),
                List.of(Tck.tuple(SUBJECT_A, Aggregation.distinct(DIMENSION), window)))
            .get(0);
    assertValue(result, 3);
    assertThat(successValue(result).exactness()).isEqualTo(Exactness.EXACT);
    assertThat(successValue(result).asOf()).isEqualTo(clock.instant());
  }

  /** Re-applying the same member does not increase cardinality. */
  public void deDupesRepeatedMembers() {
    final Feature feature = distinctFeature(window);
    store.applyDistinct(
        Tck.apply(NS_A),
        List.of(
            new DistinctIntent(feature, SUBJECT_A, member("alice")),
            new DistinctIntent(feature, SUBJECT_A, member("alice")),
            new DistinctIntent(feature, SUBJECT_A, member("bob"))));

    assertValue(
        store
            .queryDistinct(
                Tck.query(NS_A),
                List.of(Tck.tuple(SUBJECT_A, Aggregation.distinct(DIMENSION), window)))
            .get(0),
        2);
  }

  /** The value returned by apply already reflects the post-apply cardinality (read-your-write). */
  public void applyResultReflectsCardinality() {
    final Feature feature = distinctFeature(window);
    final ApplyResult first =
        store.applyDistinct(
            Tck.apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("alice"))));
    assertValue(first.perFeature().get(0).result(), 1);

    final ApplyResult second =
        store.applyDistinct(
            Tck.apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("bob"))));
    assertValue(second.perFeature().get(0).result(), 2);
  }

  /** Distinct members for one subject do not leak into another subject's cardinality. */
  public void valuesIsolatedBySubject() {
    final Feature feature = distinctFeature(window);
    store.applyDistinct(
        Tck.apply(NS_A), List.of(new DistinctIntent(feature, SUBJECT_A, member("alice"))));

    assertValue(
        store
            .queryDistinct(
                Tck.query(NS_A),
                List.of(Tck.tuple(SUBJECT_B, Aggregation.distinct(DIMENSION), window)))
            .get(0),
        0);
  }
}
