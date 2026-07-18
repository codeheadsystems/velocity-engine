// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WindowBoundsTest {

  private static final Instant START = Instant.parse("2026-07-18T00:00:00Z");
  private static final Instant END = Instant.parse("2026-07-18T01:00:00Z");

  @Test
  void acceptsEndAfterStart() {
    WindowBounds bounds = new WindowBounds(START, END);
    assertThat(bounds.start()).isEqualTo(START);
    assertThat(bounds.end()).isEqualTo(END);
  }

  @Test
  void acceptsEqualStartAndEnd() {
    assertThat(new WindowBounds(START, START).end()).isEqualTo(START);
  }

  @Test
  void rejectsEndBeforeStart() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new WindowBounds(/* start= */ END, /* end= */ START));
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new WindowBounds(null, END));
    assertThatNullPointerException().isThrownBy(() -> new WindowBounds(START, null));
  }
}
