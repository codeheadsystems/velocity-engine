// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import java.time.Instant;
import java.util.Objects;

/**
 * The window-bounds arithmetic the {@link InMemoryVelocityBackend} computes at read time (ADR 0003:
 * the backend owns time-shaping). Exposed as a small pure utility so both the backend and the
 * conformance TCK compute the <em>same</em> bounds — a scenario can seed exactly the bucket a query
 * will later merge.
 *
 * <p>Semantics (FR-14):
 *
 * <ul>
 *   <li><strong>Sliding</strong> {@code D}: bounds {@code (now - D, now]} — the value covers events
 *       with a timestamp strictly after {@code now - D} and at or before {@code now}. Reported
 *       {@link WindowBounds} are {@code [now - D, now]}.
 *   <li><strong>Tumbling</strong> {@code D}: the aligned bucket {@code [floor(nowMs/Dms)·Dms,
 *       +Dms)} — start-inclusive, end-exclusive. Edge-approximate at the current boundary is
 *       intended (FR-14): a query near the end of a bucket sees only that bucket, not a rolling
 *       window.
 * </ul>
 */
public final class WindowMath {

  private WindowMath() {}

  /**
   * The concrete {@link WindowBounds} a value over {@code window} covers as of {@code now}.
   *
   * @param window the window (duration + type)
   * @param now the backend-clock instant the read is as-of
   * @return the reported bounds for the window at {@code now}
   */
  public static WindowBounds boundsAt(final Window window, final Instant now) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(now, "now");
    return switch (window.type()) {
      case SLIDING -> new WindowBounds(now.minus(window.duration()), now);
      case TUMBLING -> tumblingBucket(now, window.duration().toMillis());
    };
  }

  /**
   * The aligned tumbling bucket {@code [floor(nowMs/durationMs)·durationMs, +durationMs)} covering
   * {@code now}.
   *
   * @param now the instant to align
   * @param durationMs the bucket width in milliseconds; must be strictly positive
   * @return the bucket's bounds
   */
  public static WindowBounds tumblingBucket(final Instant now, final long durationMs) {
    Objects.requireNonNull(now, "now");
    if (durationMs <= 0) {
      throw new IllegalArgumentException("durationMs must be positive, was " + durationMs);
    }
    final long nowMs = now.toEpochMilli();
    final long startMs = Math.floorDiv(nowMs, durationMs) * durationMs;
    return new WindowBounds(
        Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(startMs + durationMs));
  }

  /**
   * Whether an event at {@code timestamp} falls inside {@code window} whose reported {@code bounds}
   * were computed by {@link #boundsAt}. Sliding is {@code (start, end]}; tumbling is {@code [start,
   * end)}.
   *
   * @param window the window whose membership rule to apply
   * @param bounds the bounds produced by {@link #boundsAt} for the same read
   * @param timestamp the event timestamp to test
   * @return {@code true} if the event contributes to the window's value
   */
  public static boolean contains(
      final Window window, final WindowBounds bounds, final Instant timestamp) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(bounds, "bounds");
    Objects.requireNonNull(timestamp, "timestamp");
    return switch (window.type()) {
      case SLIDING -> timestamp.isAfter(bounds.start()) && !timestamp.isAfter(bounds.end());
      case TUMBLING -> !timestamp.isBefore(bounds.start()) && timestamp.isBefore(bounds.end());
    };
  }
}
