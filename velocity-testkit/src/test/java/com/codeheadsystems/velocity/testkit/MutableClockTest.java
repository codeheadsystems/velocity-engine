// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Unit tests for the test-controllable {@link MutableClock}. */
class MutableClockTest {

  private static final Instant START = Instant.parse("2026-07-18T12:00:00Z");

  @Test
  void reportsTheStartInstantAndMillis() {
    final MutableClock clock = new MutableClock(START);
    assertThat(clock.instant()).isEqualTo(START);
    assertThat(clock.millis()).isEqualTo(START.toEpochMilli());
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void advanceMovesForward() {
    final MutableClock clock = new MutableClock(START);
    clock.advance(Duration.ofSeconds(90));
    assertThat(clock.instant()).isEqualTo(START.plusSeconds(90));
  }

  @Test
  void setInstantReplacesTime() {
    final MutableClock clock = new MutableClock(START);
    clock.setInstant(START.minusSeconds(10));
    assertThat(clock.instant()).isEqualTo(START.minusSeconds(10));
  }

  @Test
  void withZoneKeepsInstantButChangesZone() {
    final MutableClock clock = new MutableClock(START);
    final Clock tokyo = clock.withZone(ZoneId.of("Asia/Tokyo"));
    assertThat(tokyo.instant()).isEqualTo(START);
    assertThat(tokyo.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
  }

  @Test
  void atNowIsCloseToSystemTime() {
    final long before = System.currentTimeMillis();
    final MutableClock clock = MutableClock.atNow();
    assertThat(clock.millis()).isGreaterThanOrEqualTo(before);
  }
}
