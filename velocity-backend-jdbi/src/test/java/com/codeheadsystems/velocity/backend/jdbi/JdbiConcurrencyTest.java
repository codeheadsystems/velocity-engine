// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Acceptance #1: the Postgres upsert is atomic under concurrent load. {@code N} threads each apply
 * the same COUNT feature {@code M} times against real Postgres; the final bucket count must be
 * exactly {@code N*M}, never lost to a read-modify-write race (NFR-6). This is what the {@code
 * INSERT ... ON CONFLICT DO UPDATE SET count = count + 1} row-locking upsert buys.
 */
final class JdbiConcurrencyTest extends AbstractJdbiBackendTest {

  private static final Namespace NS = new Namespace("jdbi-concurrency");
  private static final Subject SUBJECT = new Subject("card", "hot-subject");
  private static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  @Test
  void concurrentApplyToSameBucketIsExactlyAtomic() throws Exception {
    // A 1-hour tumbling bucket so every apply lands in the SAME bucket for the whole test run.
    final Feature feature =
        new Feature("jdbi.concurrent.count", Aggregation.count(), List.of(TUMBLING_1H));
    final int threads = 16;
    final int perThread = 50;
    final int expected = threads * perThread;

    final CountDownLatch ready = new CountDownLatch(threads);
    final CountDownLatch fire = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      final List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  await(fire);
                  for (int i = 0; i < perThread; i++) {
                    backend.applyCount(
                        new ApplyContext(NS, null), List.of(new CountIntent(feature, SUBJECT)));
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      fire.countDown();
      for (final Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    }

    final FeatureResult result =
        backend
            .queryCount(
                new QueryContext(NS, null),
                List.of(new QueryTuple(SUBJECT, Aggregation.count(), TUMBLING_1H)))
            .get(0);
    assertThat(result).isInstanceOf(FeatureResult.Success.class);
    assertThat(((FeatureResult.Success) result).value().value())
        .isEqualByComparingTo(BigDecimal.valueOf(expected));
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
