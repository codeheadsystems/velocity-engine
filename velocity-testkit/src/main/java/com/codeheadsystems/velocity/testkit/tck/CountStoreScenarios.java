// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SLIDING_1M;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SLIDING_5S;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.TUMBLING_1H;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.countFeature;
import static com.codeheadsystems.velocity.testkit.tck.Tck.successValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The {@link CountStore} contract: apply records events, query reads back the exact windowed count,
 * the write is reflected read-your-write, and values are isolated per namespace and subject.
 */
public final class CountStoreScenarios {

  private final CountStore store;
  private final MutableClock clock;

  /**
   * @param store a fresh count-capable backend under test
   * @param clock the backend's controllable clock
   */
  public CountStoreScenarios(final CountStore store, final MutableClock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Apply N events then query the window returns exactly N, flagged EXACT/ATOMIC. */
  public void applyThenQueryReturnsExactCount() {
    final Feature feature = countFeature(SLIDING_1M);
    store.applyCount(Tck.apply(NS_A), intents(feature, 3));

    final var results =
        store.queryCount(
            Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_1M)));
    assertThat(results).hasSize(1);
    assertValue(results.get(0), 3);
    assertThat(successValue(results.get(0)).exactness()).isEqualTo(Exactness.EXACT);
    assertThat(successValue(results.get(0)).readYourWriteLevel())
        .isEqualTo(ReadYourWriteLevel.ATOMIC);
    // The value is as-of the backend clock (FR-3/FR-7), not the caller's.
    assertThat(successValue(results.get(0)).asOf()).isEqualTo(clock.instant());
  }

  /** The value returned by apply already reflects the caller's own write (ADR 0007, NFR-7). */
  public void applyResultReflectsWriteReadYourWrite() {
    final Feature feature = countFeature(SLIDING_1M);

    final ApplyResult first = store.applyCount(Tck.apply(NS_A), intents(feature, 1));
    assertThat(first.perFeature()).hasSize(1);
    assertThat(first.perFeature().get(0).status()).isEqualTo(ApplyStatus.APPLIED);
    assertValue(first.perFeature().get(0).result(), 1);

    final ApplyResult second = store.applyCount(Tck.apply(NS_A), intents(feature, 1));
    assertValue(second.perFeature().get(0).result(), 2);
  }

  /** A multi-window feature yields one {@link PerFeature} per window (apply cardinality). */
  public void applyEmitsOneResultPerWindow() {
    final Feature feature = countFeature(SLIDING_5S, TUMBLING_1H);
    final ApplyResult result = store.applyCount(Tck.apply(NS_A), intents(feature, 1));

    assertThat(result.perFeature()).hasSize(2);
    assertThat(result.perFeature())
        .allSatisfy(pf -> assertThat(pf.status()).isEqualTo(ApplyStatus.APPLIED));
    assertThat(result.perFeature().stream().map(pf -> successValue(pf.result()).window()).toList())
        .containsExactlyInAnyOrder(SLIDING_5S, TUMBLING_1H);
    // One underlying event, not one per window: each window sees exactly the single apply.
    assertThat(result.perFeature()).allSatisfy(pf -> assertValue(pf.result(), 1));
  }

  /** Counts in one namespace are invisible in another; the other reads a known zero. */
  public void valuesIsolatedByNamespace() {
    store.applyCount(Tck.apply(NS_A), intents(countFeature(SLIDING_1M), 2));

    assertValue(
        store
            .queryCount(
                Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_1M)))
            .get(0),
        2);
    assertValue(
        store
            .queryCount(
                Tck.query(NS_B), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_1M)))
            .get(0),
        0);
  }

  /** Counts for one subject are invisible for another subject in the same namespace. */
  public void valuesIsolatedBySubject() {
    store.applyCount(
        Tck.apply(NS_A), List.of(new CountIntent(countFeature(SLIDING_1M), SUBJECT_A)));

    assertValue(
        store
            .queryCount(
                Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_1M)))
            .get(0),
        1);
    assertValue(
        store
            .queryCount(
                Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_B, Aggregation.count(), SLIDING_1M)))
            .get(0),
        0);
  }

  /**
   * Concurrent applies to the same subject are atomic: N threads each record once and the final
   * count is exactly N, never lost to a read-modify-write race (NFR-6).
   */
  public void concurrentApplyIsAtomic() throws Exception {
    final Feature feature = countFeature(SLIDING_1M);
    final int threads = 16;
    final CountDownLatch ready = new CountDownLatch(threads);
    final CountDownLatch fire = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      final List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  await(fire);
                  store.applyCount(Tck.apply(NS_A), intents(feature, 1));
                }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      fire.countDown();
      for (final Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    }
    assertValue(
        store
            .queryCount(
                Tck.query(NS_A), List.of(Tck.tuple(SUBJECT_A, Aggregation.count(), SLIDING_1M)))
            .get(0),
        threads);
  }

  private static void await(final CountDownLatch latch) {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static List<CountIntent> intents(final Feature feature, final int count) {
    final List<CountIntent> intents = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      intents.add(new CountIntent(feature, SUBJECT_A));
    }
    return intents;
  }
}
