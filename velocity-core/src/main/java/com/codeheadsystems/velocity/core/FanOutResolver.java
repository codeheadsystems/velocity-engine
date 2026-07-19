// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.FanOutResult;
import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.FeatureDefinitions;
import com.codeheadsystems.velocity.core.model.SubjectSource;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.Subject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one {@code record()} event into backend-neutral write intents grouped by backend
 * (FR-18).
 *
 * <p>For each definition in the namespace's snapshot the resolver decides the target {@link
 * Subject} (the event's own subject for a {@link SubjectSource.Primary primary} source when the
 * type matches; a dimension-derived subject for a {@link SubjectSource.FromDimension} source when
 * that dimension is present), builds the SPI {@link Feature} and the aggregation-specific {@link
 * Intent}, keyed-hashing DISTINCT dimension values via the {@link DimensionHasher} (FR-38). A
 * single event therefore fans out across multiple subjects and backends. A definition that applies
 * to the event's subject but cannot be written — a SUM with no value, or a DISTINCT whose dimension
 * is absent — becomes a {@code SKIPPED} outcome rather than being silently dropped.
 */
public final class FanOutResolver {

  private final DimensionHasher dimensionHasher;

  /**
   * Creates a resolver.
   *
   * @param dimensionHasher the keyed hasher for DISTINCT dimension values (FR-38)
   */
  public FanOutResolver(final DimensionHasher dimensionHasher) {
    this.dimensionHasher = Objects.requireNonNull(dimensionHasher, "dimensionHasher");
  }

  /**
   * Resolves the fan-out of one event.
   *
   * @param namespace the namespace (selects DISTINCT salt)
   * @param primarySubject the event's own subject
   * @param dimensions the event's dimensions (used for dimension-derived subjects and DISTINCT)
   * @param valueCents the event's SUM value in integer cents, or null if absent
   * @param definitions the namespace's current definition snapshot
   * @return the intents grouped by backend plus any skipped-with-reason outcomes
   */
  public FanOutResult resolve(
      final Namespace namespace,
      final Subject primarySubject,
      final Map<String, String> dimensions,
      final @Nullable BigDecimal valueCents,
      final FeatureDefinitions definitions) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(primarySubject, "primarySubject");
    Objects.requireNonNull(dimensions, "dimensions");
    Objects.requireNonNull(definitions, "definitions");

    final Map<String, List<Intent>> byBackend = new LinkedHashMap<>();
    final List<PerFeature> skipped = new ArrayList<>();

    for (final FeatureDefinition definition : definitions.definitions()) {
      final Subject subject = targetSubject(definition, primarySubject, dimensions);
      if (subject == null) {
        continue; // The definition does not apply to this event (FR-4 no-op).
      }
      final Feature feature =
          new Feature(definition.name(), definition.aggregation(), definition.windows());
      final Intent intent =
          buildIntent(namespace, definition, feature, subject, dimensions, valueCents, skipped);
      if (intent != null) {
        byBackend.computeIfAbsent(definition.backend(), unused -> new ArrayList<>()).add(intent);
      }
    }
    return new FanOutResult(byBackend, skipped);
  }

  private static @Nullable Subject targetSubject(
      final FeatureDefinition definition,
      final Subject primarySubject,
      final Map<String, String> dimensions) {
    return switch (definition.subjectSource()) {
      case SubjectSource.Primary ignored ->
          primarySubject.type().equals(definition.subjectType()) ? primarySubject : null;
      case SubjectSource.FromDimension fromDimension -> {
        final String value = dimensions.get(fromDimension.dimensionName());
        yield present(value) ? new Subject(definition.subjectType(), value) : null;
      }
    };
  }

  private @Nullable Intent buildIntent(
      final Namespace namespace,
      final FeatureDefinition definition,
      final Feature feature,
      final Subject subject,
      final Map<String, String> dimensions,
      final @Nullable BigDecimal valueCents,
      final List<PerFeature> skipped) {
    final Aggregation aggregation = definition.aggregation();
    return switch (aggregation.type()) {
      case COUNT -> new CountIntent(feature, subject);
      case SUM -> {
        if (valueCents == null) {
          skipped.add(
              skip(feature, "SUM feature '" + feature.name() + "' requires an event value"));
          yield null;
        }
        if (valueCents.scale() != 0) {
          skipped.add(
              skip(
                  feature,
                  "SUM feature '"
                      + feature.name()
                      + "' requires integer cents (scale 0), was scale "
                      + valueCents.scale()));
          yield null;
        }
        yield new SumIntent(feature, subject, valueCents);
      }
      case DISTINCT -> {
        // The aggregation's dimension is the value counted; may differ from a FromDimension
        // subject.
        final String dimensionName = Objects.requireNonNull(aggregation.dimension());
        final String value = dimensions.get(dimensionName);
        if (!present(value)) {
          skipped.add(
              skip(
                  feature,
                  "DISTINCT feature '"
                      + feature.name()
                      + "' requires dimension '"
                      + dimensionName
                      + "'"));
          yield null;
        }
        yield new DistinctIntent(feature, subject, dimensionHasher.hash(namespace, value));
      }
    };
  }

  private static PerFeature skip(final Feature feature, final String reason) {
    return new PerFeature(
        feature, ApplyStatus.SKIPPED, FeatureResult.failure(FailureCode.VALIDATION, reason));
  }

  private static boolean present(final @Nullable String value) {
    return value != null && !value.isBlank();
  }
}
