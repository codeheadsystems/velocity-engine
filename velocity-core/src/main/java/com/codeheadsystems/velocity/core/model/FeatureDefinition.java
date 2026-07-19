// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.Window;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The configuration for a single feature (FR-16): what event pattern feeds it, its subject, its
 * aggregation, the windows it is tracked over, and the backend that provides it.
 *
 * <p>A feature is bound to exactly one backend (FR-16/FR-29). Definitions are data, not code — they
 * are imported/exported as YAML (FR-28) and hot-reloaded at runtime (FR-17).
 *
 * @param name the stable, non-blank feature name (e.g. {@code card.count.1h})
 * @param subjectType the non-blank subject type this feature keys on (e.g. {@code card}, {@code
 *     ip})
 * @param subjectSource where the fan-out subject comes from — the event's primary subject or a
 *     dimension-derived one (FR-18)
 * @param aggregation what the feature aggregates (COUNT/SUM/DISTINCT)
 * @param windows the non-empty set of windows the feature is tracked over
 * @param backend the non-blank name of the backend that provides this feature (FR-16)
 * @param distinctThreshold the per-{@code (subject, bucket)} exact→HLL threshold for DISTINCT
 *     features (ADR 0006); {@code null} means "use the backend default", and it MUST be null for
 *     non-DISTINCT features
 */
public record FeatureDefinition(
    String name,
    String subjectType,
    SubjectSource subjectSource,
    Aggregation aggregation,
    List<Window> windows,
    String backend,
    @Nullable Long distinctThreshold) {

  /** Validates the definition's invariants and stores an unmodifiable copy of the windows. */
  public FeatureDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(subjectType, "subjectType");
    Objects.requireNonNull(subjectSource, "subjectSource");
    Objects.requireNonNull(aggregation, "aggregation");
    Objects.requireNonNull(windows, "windows");
    Objects.requireNonNull(backend, "backend");
    if (name.isBlank()) {
      throw new IllegalArgumentException("feature name must not be blank");
    }
    if (subjectType.isBlank()) {
      throw new IllegalArgumentException("subjectType must not be blank");
    }
    if (backend.isBlank()) {
      throw new IllegalArgumentException("backend must not be blank");
    }
    if (windows.isEmpty()) {
      throw new IllegalArgumentException("feature '" + name + "' must declare at least one window");
    }
    if (aggregation.type() != AggregationType.DISTINCT && distinctThreshold != null) {
      throw new IllegalArgumentException(
          "distinctThreshold is only valid for DISTINCT features, was set on '" + name + "'");
    }
    windows = List.copyOf(windows);
  }

  /**
   * A COUNT feature over the event's own subject.
   *
   * @param name the feature name
   * @param subjectType the subject type
   * @param backend the backend name
   * @param windows the windows
   * @return a primary-subject COUNT definition
   */
  public static FeatureDefinition count(
      final String name,
      final String subjectType,
      final String backend,
      final List<Window> windows) {
    return new FeatureDefinition(
        name, subjectType, SubjectSource.primary(), Aggregation.count(), windows, backend, null);
  }

  /**
   * A SUM feature over the event's own subject.
   *
   * @param name the feature name
   * @param subjectType the subject type
   * @param backend the backend name
   * @param windows the windows
   * @return a primary-subject SUM definition
   */
  public static FeatureDefinition sum(
      final String name,
      final String subjectType,
      final String backend,
      final List<Window> windows) {
    return new FeatureDefinition(
        name, subjectType, SubjectSource.primary(), Aggregation.sum(), windows, backend, null);
  }

  /**
   * A DISTINCT feature over the given dimension, keyed on the event's own subject.
   *
   * @param name the feature name
   * @param subjectType the subject type
   * @param dimension the dimension whose distinct values are counted
   * @param backend the backend name
   * @param windows the windows
   * @return a primary-subject DISTINCT definition with the backend-default threshold
   */
  public static FeatureDefinition distinct(
      final String name,
      final String subjectType,
      final String dimension,
      final String backend,
      final List<Window> windows) {
    return new FeatureDefinition(
        name,
        subjectType,
        SubjectSource.primary(),
        Aggregation.distinct(dimension),
        windows,
        backend,
        null);
  }

  /**
   * The largest window duration this feature is tracked over — the retention floor its backend must
   * satisfy (FR-22a).
   *
   * @return the longest window duration
   */
  public java.time.Duration largestWindow() {
    java.time.Duration largest = windows.get(0).duration();
    for (final Window window : windows) {
      if (window.duration().compareTo(largest) > 0) {
        largest = window.duration();
      }
    }
    return largest;
  }
}
