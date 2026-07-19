// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A test-controllable {@link Clock} whose instant can be set or advanced by hand.
 *
 * <p>The backend is the clock authority for windowing (FR-3, ADR 0003), so the conformance TCK
 * needs to move time deterministically — age an event out of a sliding window, cross a tumbling
 * bucket boundary — without sleeping. Inject one of these into any backend that accepts a {@link
 * Clock} (the {@link InMemoryVelocityBackend} does) and the same instance drives both the backend's
 * bucketing and the scenario's expectations.
 *
 * <p>The stored instant is {@code volatile} so an advance on one thread is visible to a concurrent
 * apply/query on another; it is not a substitute for the backend's own per-key locking.
 */
public final class MutableClock extends Clock {

  private final ZoneId zone;
  private volatile Instant instant;

  /**
   * Creates a clock fixed at {@code start} in UTC.
   *
   * @param start the initial instant
   */
  public MutableClock(final Instant start) {
    this(start, ZoneOffset.UTC);
  }

  private MutableClock(final Instant start, final ZoneId zone) {
    this.instant = Objects.requireNonNull(start, "start");
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  /**
   * A clock fixed at the current system instant, truncated to milliseconds so bucket-boundary math
   * is free of sub-millisecond noise.
   *
   * @return a new mutable clock at "now"
   */
  public static MutableClock atNow() {
    return new MutableClock(Instant.ofEpochMilli(System.currentTimeMillis()));
  }

  /**
   * Sets the clock to an explicit instant.
   *
   * @param newInstant the instant the clock should report from now on
   */
  public void setInstant(final Instant newInstant) {
    this.instant = Objects.requireNonNull(newInstant, "newInstant");
  }

  /**
   * Advances the clock forward by the given amount.
   *
   * @param amount the (non-negative) duration to move forward
   */
  public void advance(final Duration amount) {
    Objects.requireNonNull(amount, "amount");
    this.instant = this.instant.plus(amount);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(final ZoneId newZone) {
    return new MutableClock(instant, newZone);
  }

  @Override
  public Instant instant() {
    return instant;
  }

  @Override
  public long millis() {
    return instant.toEpochMilli();
  }
}
