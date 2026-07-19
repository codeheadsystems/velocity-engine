// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import java.util.List;

/**
 * The {@code COUNT} aggregation mix-in (ADR 0003): apply and query count intents.
 *
 * <p>A backend implements this only if it declares {@link
 * com.codeheadsystems.velocity.spi.model.AggregationType#COUNT COUNT} in its {@link
 * com.codeheadsystems.velocity.spi.model.BackendCapabilities capabilities}. Each returned {@link
 * FeatureResult} is a value or a distinguishable failure — never a silent {@code 0} (ADR 0009).
 */
public interface CountStore extends VelocityBackend {

  /**
   * Applies count writes. Returns an {@link ApplyResult} carrying, per intent, the {@link
   * com.codeheadsystems.velocity.spi.model.ApplyStatus apply status} ({@code
   * APPLIED|FAILED|SKIPPED} — a {@code SKIPPED} being a backend-owned outcome such as an idempotent
   * replay) alongside the {@link FeatureResult} (ADR 0009, FR-34).
   *
   * <p>The result cardinality is <strong>one {@link
   * com.codeheadsystems.velocity.spi.model.PerFeature PerFeature} per {@code (intent × each of the
   * intent's feature's windows)}</strong>, not one per intent: a feature tracked over several
   * windows contributes one entry per window, each carrying that window's post-apply value. The
   * entries are therefore not positionally aligned with {@code intents}; disambiguate by the
   * entry's feature and its {@link com.codeheadsystems.velocity.spi.model.FeatureValue#window()
   * window}.
   *
   * @param ctx the namespace + optional deadline context
   * @param intents the count intents to apply
   * @return one apply outcome per {@code (intent × the intent's feature's windows)}
   */
  ApplyResult applyCount(ApplyContext ctx, List<CountIntent> intents);

  /**
   * Queries counts, returning one result per tuple in order.
   *
   * @param ctx the namespace + optional deadline context
   * @param tuples the query tuples to read
   * @return the per-tuple results, positionally aligned with {@code tuples}
   */
  List<FeatureResult> queryCount(QueryContext ctx, List<QueryTuple> tuples);
}
