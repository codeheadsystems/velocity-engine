// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureTest {

  private static final Window HOUR = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  @Test
  void rejectsBlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Feature("  ", Aggregation.count(), List.of(HOUR)));
  }

  @Test
  void rejectsEmptyWindows() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Feature("card.count.1h", Aggregation.count(), List.of()));
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Feature(null, Aggregation.count(), List.of(HOUR)));
    assertThatNullPointerException().isThrownBy(() -> new Feature("f", null, List.of(HOUR)));
    assertThatNullPointerException().isThrownBy(() -> new Feature("f", Aggregation.count(), null));
  }

  @Test
  void defensivelyCopiesWindows() {
    List<Window> source = new ArrayList<>(List.of(HOUR));
    Feature feature = new Feature("card.count.1h", Aggregation.count(), source);

    source.add(new Window(Duration.ofDays(1), WindowType.TUMBLING));

    assertThat(feature.windows()).containsExactly(HOUR);
  }

  @Test
  void windowsAreUnmodifiable() {
    Feature feature = new Feature("card.count.1h", Aggregation.count(), List.of(HOUR));
    assertThat(feature.windows()).isUnmodifiable();
  }
}
