// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import java.util.List;

/**
 * The {@code DISTINCT} aggregation mix-in (ADR 0003): apply and query distinct-cardinality intents.
 *
 * <p>Distinct is <strong>exact where cheap, HLL above a per-{@code (subject, bucket)}
 * threshold</strong> (ADR 0006): a bucket starts exact and, once it crosses the backend-clamped
 * threshold, becomes permanently approximate; a multi-bucket window is approximate if any
 * constituent bucket is HLL, with exact buckets HLL-folded at read time. HLL is
 * <strong>tumbling-only</strong> (ADR 0005) — sliding distinct is exact and capped ({@link
 * com.codeheadsystems.velocity.spi.model.FailureCode#CARDINALITY_CAP_EXCEEDED}). A backend
 * implements this only if it declares {@link
 * com.codeheadsystems.velocity.spi.model.AggregationType#DISTINCT DISTINCT}. Members are opaque,
 * pre-hashed tokens the backend must not interpret. Each returned {@link FeatureResult} is a value
 * or a distinguishable failure — never a silent {@code 0} (ADR 0009).
 */
public interface DistinctStore extends VelocityBackend {

  /**
   * Applies distinct writes. Returns an {@link ApplyResult} carrying, per intent, the {@link
   * com.codeheadsystems.velocity.spi.model.ApplyStatus apply status} ({@code
   * APPLIED|FAILED|SKIPPED}) alongside the {@link FeatureResult} (ADR 0009, FR-34).
   *
   * @param ctx the namespace + optional deadline context
   * @param intents the distinct intents to apply
   * @return the per-intent apply outcomes, positionally aligned with {@code intents}
   */
  ApplyResult applyDistinct(ApplyContext ctx, List<DistinctIntent> intents);

  /**
   * Queries distinct cardinalities, returning one result per tuple in order.
   *
   * @param ctx the namespace + optional deadline context
   * @param tuples the query tuples to read
   * @return the per-tuple results, positionally aligned with {@code tuples}
   */
  List<FeatureResult> queryDistinct(QueryContext ctx, List<QueryTuple> tuples);
}
