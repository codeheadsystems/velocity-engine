// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model;

import com.codeheadsystems.velocity.spi.model.Intent;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The resolved fan-out of one {@code record()} event (FR-18): the backend-neutral write intents
 * grouped by their target backend, plus the outcomes for features that matched the event but could
 * not be applied.
 *
 * <p>A single event fans out across multiple subjects and backends (FR-18); {@code
 * intentsByBackend} keys the intents by backend name so the engine can dispatch each group to its
 * backend and sub-group by aggregation. {@code skipped} carries the pre-computed {@link PerFeature}
 * outcomes for definitions that applied to this event's subject but were skipped — a SUM whose
 * value was missing, or a DISTINCT whose dimension was absent — so they surface in the aggregate
 * result rather than being silently dropped.
 *
 * @param intentsByBackend the write intents grouped by target backend name; defensively copied
 * @param skipped the pre-computed outcomes for matched-but-not-applied features; defensively copied
 */
public record FanOutResult(Map<String, List<Intent>> intentsByBackend, List<PerFeature> skipped) {

  /** Stores unmodifiable copies of both collections. */
  public FanOutResult {
    Objects.requireNonNull(intentsByBackend, "intentsByBackend");
    Objects.requireNonNull(skipped, "skipped");
    intentsByBackend = Map.copyOf(intentsByBackend);
    skipped = List.copyOf(skipped);
  }

  /**
   * Whether this fan-out produced no intents and no skipped outcomes (FR-4: no matching
   * definition).
   *
   * @return {@code true} if nothing matched the event
   */
  public boolean isEmpty() {
    return intentsByBackend.isEmpty() && skipped.isEmpty();
  }
}
