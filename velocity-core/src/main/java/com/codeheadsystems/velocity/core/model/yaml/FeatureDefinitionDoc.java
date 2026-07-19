// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model.yaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The YAML wire shape of one feature definition (FR-28).
 *
 * @param name the feature name
 * @param subjectType the subject type
 * @param subjectSource where the fan-out subject comes from (defaults to primary when absent)
 * @param aggregation the aggregation (type + optional distinct dimension)
 * @param windows the windows the feature is tracked over
 * @param backend the backend that provides this feature
 * @param distinctThreshold the DISTINCT exact→HLL threshold; null uses the backend default
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureDefinitionDoc(
    @Nullable String name,
    @Nullable String subjectType,
    @Nullable SubjectSourceDoc subjectSource,
    @Nullable AggregationDoc aggregation,
    @Nullable List<WindowDoc> windows,
    @Nullable String backend,
    @Nullable Long distinctThreshold) {

  /**
   * The YAML shape of a subject source: {@code kind: PRIMARY} or {@code kind: FROM_DIMENSION} with
   * a {@code dimension}.
   *
   * @param kind {@code PRIMARY} or {@code FROM_DIMENSION}
   * @param dimension the dimension name (only for {@code FROM_DIMENSION})
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SubjectSourceDoc(@Nullable String kind, @Nullable String dimension) {}

  /**
   * The YAML shape of an aggregation: a {@code type} and, for {@code DISTINCT}, a {@code
   * dimension}.
   *
   * @param type {@code COUNT}, {@code SUM}, or {@code DISTINCT}
   * @param dimension the distinct dimension (only for {@code DISTINCT})
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AggregationDoc(@Nullable String type, @Nullable String dimension) {}

  /**
   * The YAML shape of a window: an ISO-8601 {@code duration} (e.g. {@code PT1H}) and a {@code
   * type}.
   *
   * @param duration the ISO-8601 duration string
   * @param type {@code SLIDING} or {@code TUMBLING}
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WindowDoc(@Nullable String duration, @Nullable String type) {}
}
