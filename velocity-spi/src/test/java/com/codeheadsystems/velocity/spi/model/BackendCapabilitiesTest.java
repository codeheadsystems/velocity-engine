// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.codeheadsystems.velocity.spi.model.BackendCapabilities.WindowSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackendCapabilitiesTest {

  private static final Window HOUR = new Window(Duration.ofHours(1), WindowType.TUMBLING);
  private static final WindowSpec HOUR_SPEC =
      new WindowSpec(HOUR, Exactness.EXACT, Duration.ofMinutes(5));

  private static BackendCapabilities capabilities(boolean distinctHllSliding) {
    return new BackendCapabilities(
        EnumSet.of(AggregationType.COUNT, AggregationType.SUM),
        List.of(HOUR_SPEC),
        distinctHllSliding,
        10_000L,
        10_000L,
        Duration.ofDays(30),
        ReadYourWriteLevel.ATOMIC,
        true,
        false,
        100);
  }

  @Test
  void rejectsDistinctHllSliding() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> capabilities(true))
        .withMessageContaining("distinctHllSliding");
  }

  @Test
  void acceptsDistinctHllSlidingFalse() {
    assertThat(capabilities(false).distinctHllSliding()).isFalse();
  }

  @Test
  void supportsAggregationReflectsDeclaredSet() {
    BackendCapabilities caps = capabilities(false);
    assertThat(caps.supportsAggregation(AggregationType.COUNT)).isTrue();
    assertThat(caps.supportsAggregation(AggregationType.SUM)).isTrue();
    assertThat(caps.supportsAggregation(AggregationType.DISTINCT)).isFalse();
  }

  @Test
  void supportsWindowMatchesDeclaredWindow() {
    BackendCapabilities caps = capabilities(false);
    assertThat(caps.supportsWindow(HOUR)).isTrue();
    assertThat(caps.supportsWindow(new Window(Duration.ofDays(1), WindowType.TUMBLING))).isFalse();
    assertThat(caps.supportsWindow(new Window(Duration.ofHours(1), WindowType.SLIDING))).isFalse();
  }

  @Test
  void defensivelyCopiesAggregationsAndWindows() {
    Set<AggregationType> aggregations = EnumSet.of(AggregationType.COUNT);
    List<WindowSpec> windows = new ArrayList<>(List.of(HOUR_SPEC));
    BackendCapabilities caps =
        new BackendCapabilities(
            aggregations,
            windows,
            false,
            10_000L,
            10_000L,
            Duration.ofDays(30),
            ReadYourWriteLevel.ATOMIC,
            true,
            false,
            100);

    aggregations.add(AggregationType.DISTINCT);
    windows.add(
        new WindowSpec(
            new Window(Duration.ofDays(1), WindowType.TUMBLING),
            Exactness.APPROXIMATE,
            Duration.ofHours(1)));

    assertThat(caps.supportsAggregation(AggregationType.DISTINCT)).isFalse();
    assertThat(caps.windows()).containsExactly(HOUR_SPEC);
    assertThat(caps.windows()).isUnmodifiable();
  }
}
