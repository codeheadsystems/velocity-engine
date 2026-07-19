// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.FeatureDefinitions;
import com.codeheadsystems.velocity.spi.model.Namespace;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MutableFeatureDefinitionProviderTest {

  private static final Namespace ACME = new Namespace("acme");
  private final MutableFeatureDefinitionProvider provider = new MutableFeatureDefinitionProvider();

  @Test
  void unknownNamespaceReturnsEmptySnapshot() {
    final FeatureDefinitions snapshot = provider.definitions(ACME);
    assertThat(snapshot.isEmpty()).isTrue();
    assertThat(snapshot.versionHash()).isNotBlank();
  }

  @Test
  void reloadSwapsSnapshotAndChangesVersion() {
    final String empty = provider.definitions(ACME).versionHash();
    provider.reload(ACME, List.of(Fixtures.cardCount()));
    final FeatureDefinitions after = provider.definitions(ACME);
    assertThat(after.versionHash()).isNotEqualTo(empty);
    assertThat(after.definition("card.count")).isPresent();
  }

  @Test
  void concurrentReadsAlwaysSeeAWholeSnapshot() throws InterruptedException {
    final List<FeatureDefinition> setA = List.of(Fixtures.cardCount());
    final List<FeatureDefinition> setB =
        List.of(Fixtures.cardCount(), Fixtures.cardSum(), Fixtures.cardDistinctIp());
    provider.reload(ACME, setA);

    final Set<Integer> observedSizes = ConcurrentHashMap.newKeySet();
    final int readers = 6;
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(readers + 1);

    final Thread writer =
        new Thread(
            () -> {
              await(start);
              for (int i = 0; i < 2000; i++) {
                provider.reload(ACME, (i % 2 == 0) ? setA : setB);
              }
              done.countDown();
            });
    writer.start();

    for (int r = 0; r < readers; r++) {
      new Thread(
              () -> {
                await(start);
                for (int i = 0; i < 5000; i++) {
                  observedSizes.add(provider.definitions(ACME).definitions().size());
                }
                done.countDown();
              })
          .start();
    }

    start.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    // Every observation was a whole snapshot — only setA's or setB's size, never a torn count.
    assertThat(observedSizes).isSubsetOf(setA.size(), setB.size());
  }

  private static void await(final CountDownLatch latch) {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
