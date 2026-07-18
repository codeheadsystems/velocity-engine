// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplyResultTest {

  private static PerFeature perFeature() {
    Window window = new Window(Duration.ofHours(1), WindowType.TUMBLING);
    Feature feature = new Feature("card.count.1h", Aggregation.count(), List.of(window));
    Instant now = Instant.parse("2026-07-18T01:00:00Z");
    FeatureValue value =
        new FeatureValue(
            feature,
            window,
            new BigDecimal("1"),
            Exactness.EXACT,
            ReadYourWriteLevel.ATOMIC,
            null,
            new WindowBounds(now.minus(Duration.ofHours(1)), now),
            now);
    return new PerFeature(feature, ApplyStatus.APPLIED, FeatureResult.success(value));
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new ApplyResult(null));
  }

  @Test
  void defensivelyCopiesInput() {
    List<PerFeature> source = new ArrayList<>(List.of(perFeature()));
    ApplyResult result = new ApplyResult(source);

    source.add(perFeature());

    assertThat(result.perFeature()).hasSize(1);
  }

  @Test
  void perFeatureIsUnmodifiable() {
    ApplyResult result = new ApplyResult(List.of(perFeature()));
    assertThat(result.perFeature()).isUnmodifiable();
  }
}
