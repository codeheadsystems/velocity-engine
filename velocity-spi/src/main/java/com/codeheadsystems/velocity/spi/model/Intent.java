// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A backend-neutral write intent {@code (feature, subject, member|value)} (ADR 0003).
 *
 * <p>{@code velocity-core} resolves an incoming {@code record()} against the matching feature
 * definitions down to intents; the intent names the feature (which carries its windows) but says
 * nothing about bucket keys, TTLs, sliding ranges, or eviction — the backend owns all of that.
 *
 * <p>Each variant validates that the feature's aggregation type matches its kind, so a {@code
 * CountIntent} can only ever carry a {@code COUNT} feature, and so on.
 */
public sealed interface Intent permits Intent.CountIntent, Intent.SumIntent, Intent.DistinctIntent {

  /**
   * The feature this intent writes to.
   *
   * @return the feature
   */
  Feature feature();

  /**
   * The subject the write is attributed to.
   *
   * @return the subject
   */
  Subject subject();

  /**
   * A COUNT write intent (neither member nor value).
   *
   * @param feature a feature whose aggregation type is {@code COUNT}
   * @param subject the subject
   */
  record CountIntent(Feature feature, Subject subject) implements Intent {

    /** Validates the feature is a COUNT feature. */
    public CountIntent {
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(subject, "subject");
      if (feature.aggregation().type() != AggregationType.COUNT) {
        throw new IllegalArgumentException(
            "CountIntent requires a COUNT feature, was " + feature.aggregation().type());
      }
    }
  }

  /**
   * A SUM write intent carrying an integer-cents value (P3/FR-10; may be negative for refunds).
   *
   * @param feature a feature whose aggregation type is {@code SUM}
   * @param subject the subject
   * @param valueCents the amount in integer cents; its {@link BigDecimal#scale() scale} must be 0
   */
  record SumIntent(Feature feature, Subject subject, BigDecimal valueCents) implements Intent {

    /** Validates the feature is a SUM feature and the value is integer cents (scale 0). */
    public SumIntent {
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(subject, "subject");
      Objects.requireNonNull(valueCents, "valueCents");
      if (feature.aggregation().type() != AggregationType.SUM) {
        throw new IllegalArgumentException(
            "SumIntent requires a SUM feature, was " + feature.aggregation().type());
      }
      if (valueCents.scale() != 0) {
        throw new IllegalArgumentException(
            "valueCents must be integer cents (scale 0), was scale " + valueCents.scale());
      }
    }
  }

  /**
   * A DISTINCT write intent carrying the opaque, pre-hashed dimension member.
   *
   * @param feature a feature whose aggregation type is {@code DISTINCT}
   * @param subject the subject
   * @param member the opaque distinct member
   */
  record DistinctIntent(Feature feature, Subject subject, DistinctMember member) implements Intent {

    /** Validates the feature is a DISTINCT feature. */
    public DistinctIntent {
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(subject, "subject");
      Objects.requireNonNull(member, "member");
      if (feature.aggregation().type() != AggregationType.DISTINCT) {
        throw new IllegalArgumentException(
            "DistinctIntent requires a DISTINCT feature, was " + feature.aggregation().type());
      }
    }
  }
}
