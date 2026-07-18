// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The single source of truth for what a backend can do (ADR 0003, §6.1).
 *
 * <p>The engine reads this at runtime (never hardcoded — P18) to validate feature definitions and
 * requests (FR-12/13/29) and to shape defaults. The set of mix-in interfaces a backend implements
 * MUST agree with what it declares here; the conformance TCK (ADR 0004) asserts that agreement,
 * including the negatives.
 *
 * @param aggregations the supported aggregation kinds; defensively copied
 * @param windows the supported window specs (duration/type/exactness/granularity); defensively
 *     copied
 * @param distinctHllSliding MUST be {@code false} — HLL-distinct is invalid on sliding windows (ADR
 *     0005); the field exists only so the TCK can assert the negative
 * @param distinctExactCardinalityClamp the backend's maximum exact-distinct cardinality; the engine
 *     clamps a per-feature threshold down to this (ADR 0006)
 * @param distinctHllThresholdDefault the default exact→HLL threshold per {@code (subject, bucket)}
 *     (ADR 0006, default 10,000)
 * @param maxRetention the retention ceiling that bounds how far back windows can reach (FR-22a)
 * @param readYourWriteLevel the backend's declared read-your-write level (ADR 0007)
 * @param idempotencySupported whether the backend supports idempotent apply (FR-5, §15 R15)
 * @param seedSupported whether the backend implements {@code SeedSupport} (ADR 0008)
 * @param maxAtomicFanOut the maximum number of intents applied atomically in one call (e.g.
 *     DynamoDB's 100-item transaction cap, §15 R3)
 */
public record BackendCapabilities(
    Set<AggregationType> aggregations,
    List<WindowSpec> windows,
    boolean distinctHllSliding,
    long distinctExactCardinalityClamp,
    long distinctHllThresholdDefault,
    Duration maxRetention,
    ReadYourWriteLevel readYourWriteLevel,
    boolean idempotencySupported,
    boolean seedSupported,
    int maxAtomicFanOut) {

  /**
   * Validates the {@code distinctHllSliding == false} invariant (ADR 0005) and stores unmodifiable
   * defensive copies of the collections.
   */
  public BackendCapabilities {
    Objects.requireNonNull(aggregations, "aggregations");
    Objects.requireNonNull(windows, "windows");
    Objects.requireNonNull(maxRetention, "maxRetention");
    Objects.requireNonNull(readYourWriteLevel, "readYourWriteLevel");
    if (distinctHllSliding) {
      throw new IllegalArgumentException(
          "distinctHllSliding must be false: HLL-distinct is invalid on sliding windows (ADR 0005)");
    }
    aggregations = Set.copyOf(aggregations);
    windows = List.copyOf(windows);
  }

  /**
   * Whether the backend supports the given aggregation kind.
   *
   * @param type the aggregation kind
   * @return {@code true} if declared supported
   */
  public boolean supportsAggregation(AggregationType type) {
    return aggregations.contains(type);
  }

  /**
   * Whether the backend supports the given window (matching a declared {@link WindowSpec}'s
   * window).
   *
   * @param window the window to check
   * @return {@code true} if a declared window spec covers this window
   */
  public boolean supportsWindow(Window window) {
    Objects.requireNonNull(window, "window");
    for (WindowSpec spec : windows) {
      if (spec.window().equals(window)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A supported window with its exactness and storage granularity (ADR 0003, §6.1).
   *
   * @param window the window (duration + type)
   * @param exactness whether values over this window are exact or approximate
   * @param granularity the bucket granularity the backend stores this window at
   */
  public record WindowSpec(Window window, Exactness exactness, Duration granularity) {

    /** Validates all components are present. */
    public WindowSpec {
      Objects.requireNonNull(window, "window");
      Objects.requireNonNull(exactness, "exactness");
      Objects.requireNonNull(granularity, "granularity");
    }
  }
}
