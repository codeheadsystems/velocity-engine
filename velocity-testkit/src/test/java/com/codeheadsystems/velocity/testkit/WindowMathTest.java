// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the window-bounds arithmetic the backend relies on. */
class WindowMathTest {

  private static final Window SLIDING_5S = new Window(Duration.ofSeconds(5), WindowType.SLIDING);
  private static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);

  @Test
  void slidingBoundsAreNowMinusDurationToNow() {
    final Instant now = Instant.ofEpochMilli(100_000);
    final WindowBounds bounds = WindowMath.boundsAt(SLIDING_5S, now);
    assertThat(bounds.start()).isEqualTo(Instant.ofEpochMilli(95_000));
    assertThat(bounds.end()).isEqualTo(now);
  }

  @Test
  void tumblingBoundsAreTheAlignedBucket() {
    final Instant now = Instant.ofEpochMilli(125_000);
    final WindowBounds bounds = WindowMath.boundsAt(TUMBLING_1M, now);
    assertThat(bounds.start()).isEqualTo(Instant.ofEpochMilli(120_000));
    assertThat(bounds.end()).isEqualTo(Instant.ofEpochMilli(180_000));
  }

  @Test
  void tumblingBucketAlignsToFloor() {
    final WindowBounds bounds = WindowMath.tumblingBucket(Instant.ofEpochMilli(179_999), 60_000);
    assertThat(bounds.start()).isEqualTo(Instant.ofEpochMilli(120_000));
    assertThat(bounds.end()).isEqualTo(Instant.ofEpochMilli(180_000));
  }

  @Test
  void tumblingBucketRejectsNonPositiveDuration() {
    assertThatThrownBy(() -> WindowMath.tumblingBucket(Instant.EPOCH, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void slidingContainsIsStartExclusiveEndInclusive() {
    final Instant now = Instant.ofEpochMilli(100_000);
    final WindowBounds bounds = WindowMath.boundsAt(SLIDING_5S, now);
    assertThat(WindowMath.contains(SLIDING_5S, bounds, bounds.start()))
        .isFalse(); // exclusive start
    assertThat(WindowMath.contains(SLIDING_5S, bounds, Instant.ofEpochMilli(95_001))).isTrue();
    assertThat(WindowMath.contains(SLIDING_5S, bounds, bounds.end())).isTrue(); // inclusive end
    assertThat(WindowMath.contains(SLIDING_5S, bounds, Instant.ofEpochMilli(100_001))).isFalse();
    assertThat(WindowMath.contains(SLIDING_5S, bounds, Instant.ofEpochMilli(94_999))).isFalse();
  }

  @Test
  void tumblingContainsIsStartInclusiveEndExclusive() {
    final Instant now = Instant.ofEpochMilli(125_000);
    final WindowBounds bounds = WindowMath.boundsAt(TUMBLING_1M, now);
    assertThat(WindowMath.contains(TUMBLING_1M, bounds, bounds.start()))
        .isTrue(); // inclusive start
    assertThat(WindowMath.contains(TUMBLING_1M, bounds, bounds.end())).isFalse(); // exclusive end
    assertThat(WindowMath.contains(TUMBLING_1M, bounds, Instant.ofEpochMilli(179_999))).isTrue();
    assertThat(WindowMath.contains(TUMBLING_1M, bounds, Instant.ofEpochMilli(119_999))).isFalse();
  }
}
