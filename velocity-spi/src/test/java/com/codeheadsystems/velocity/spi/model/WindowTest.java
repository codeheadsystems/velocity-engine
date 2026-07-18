// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WindowTest {

  @Test
  void acceptsPositiveDuration() {
    Window window = new Window(Duration.ofHours(1), WindowType.SLIDING);
    assertThat(window.duration()).isEqualTo(Duration.ofHours(1));
    assertThat(window.type()).isEqualTo(WindowType.SLIDING);
  }

  @Test
  void rejectsZeroDuration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Window(Duration.ZERO, WindowType.TUMBLING));
  }

  @Test
  void rejectsNegativeDuration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Window(Duration.ofSeconds(-1), WindowType.TUMBLING));
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new Window(null, WindowType.SLIDING));
    assertThatNullPointerException().isThrownBy(() -> new Window(Duration.ofHours(1), null));
  }
}
