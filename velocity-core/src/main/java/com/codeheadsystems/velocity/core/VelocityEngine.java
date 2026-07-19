// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.CapabilityValidationResult;
import com.codeheadsystems.velocity.core.model.CapabilityValidationResult.Violation;
import com.codeheadsystems.velocity.core.model.FanOutResult;
import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.FeatureDefinitions;
import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.SumStore;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.Intent;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The embeddable engine and public API: turns a {@code record()} into backend-neutral intents, fans
 * it out across every matching feature and backend, and merges the outcomes into one result stamped
 * with the definition version it was computed under (FR-40).
 *
 * <p>The engine owns fan-out and routing only; windowing lives entirely in the backend (ADR 0003).
 * It is constructed with a {@link BackendRegistry}, a {@link FeatureDefinitionProvider}, a {@link
 * DimensionHasher}, and a {@link CapabilityValidator} (no Dagger — DI lives in the service tier).
 *
 * <p>Read-your-write rides on the backend: because the in-process backends return an apply's
 * post-write value, {@link #record}'s result already reflects the write (ADR 0007). Every returned
 * value is re-stamped with the current snapshot's {@code versionHash} (FR-40).
 */
public final class VelocityEngine {

  private final BackendRegistry backends;
  private final FeatureDefinitionProvider definitionProvider;
  private final CapabilityValidator capabilityValidator;
  private final FanOutResolver fanOutResolver;

  /**
   * Creates an engine.
   *
   * @param backends the registry of backends features bind to
   * @param definitionProvider the source of per-namespace definition snapshots (atomic hot-reload)
   * @param dimensionHasher the keyed hasher for DISTINCT dimension values (FR-38)
   * @param capabilityValidator the validator run on reload (FR-29)
   */
  public VelocityEngine(
      final BackendRegistry backends,
      final FeatureDefinitionProvider definitionProvider,
      final DimensionHasher dimensionHasher,
      final CapabilityValidator capabilityValidator) {
    this.backends = Objects.requireNonNull(backends, "backends");
    this.definitionProvider = Objects.requireNonNull(definitionProvider, "definitionProvider");
    this.capabilityValidator = Objects.requireNonNull(capabilityValidator, "capabilityValidator");
    this.fanOutResolver =
        new FanOutResolver(Objects.requireNonNull(dimensionHasher, "dimensionHasher"));
  }

  /**
   * Records an event, fanning it out to every matching feature across every affected subject and
   * backend, and returns the aggregate post-write outcome (FR-18, ADR 0009).
   *
   * <p>An event that matches no definition is a no-op with an empty result, never an error (FR-4).
   * Each per-feature entry is stamped with the current snapshot's definition version (FR-40).
   *
   * @param namespace the tenancy scope
   * @param subject the event's primary subject
   * @param dimensions the event's dimensions (empty if none)
   * @param valueCents the event's SUM value in integer cents, or null if the event carries no value
   * @return one {@link PerFeature} outcome per touched {@code (feature × window)} plus any skipped
   *     features
   */
  public ApplyResult record(
      final Namespace namespace,
      final Subject subject,
      final Map<String, String> dimensions,
      final @Nullable BigDecimal valueCents) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(dimensions, "dimensions");
    final FeatureDefinitions definitions = definitionProvider.definitions(namespace);
    final FanOutResult fanOut =
        fanOutResolver.resolve(namespace, subject, dimensions, valueCents, definitions);
    final ApplyContext ctx = new ApplyContext(namespace, null);
    final List<PerFeature> outcomes = new ArrayList<>();
    for (final Map.Entry<String, List<Intent>> group : fanOut.intentsByBackend().entrySet()) {
      final VelocityBackend backend = backends.backend(group.getKey());
      outcomes.addAll(applyGroup(ctx, backend, group.getValue()));
    }
    outcomes.addAll(fanOut.skipped());
    return new ApplyResult(stampAll(outcomes, definitions.versionHash()));
  }

  private List<PerFeature> applyGroup(
      final ApplyContext ctx, final VelocityBackend backend, final List<Intent> intents) {
    final List<CountIntent> counts = new ArrayList<>();
    final List<SumIntent> sums = new ArrayList<>();
    final List<DistinctIntent> distincts = new ArrayList<>();
    for (final Intent intent : intents) {
      switch (intent) {
        case CountIntent count -> counts.add(count);
        case SumIntent sum -> sums.add(sum);
        case DistinctIntent distinct -> distincts.add(distinct);
      }
    }
    final List<PerFeature> outcomes = new ArrayList<>();
    if (!counts.isEmpty()) {
      if (backend instanceof CountStore store) {
        outcomes.addAll(store.applyCount(ctx, counts).perFeature());
      } else {
        outcomes.addAll(unsupported(counts, "COUNT"));
      }
    }
    if (!sums.isEmpty()) {
      if (backend instanceof SumStore store) {
        outcomes.addAll(store.applySum(ctx, sums).perFeature());
      } else {
        outcomes.addAll(unsupported(sums, "SUM"));
      }
    }
    if (!distincts.isEmpty()) {
      if (backend instanceof DistinctStore store) {
        outcomes.addAll(store.applyDistinct(ctx, distincts).perFeature());
      } else {
        outcomes.addAll(unsupported(distincts, "DISTINCT"));
      }
    }
    return outcomes;
  }

  private static List<PerFeature> unsupported(
      final List<? extends Intent> intents, final String aggregation) {
    final List<PerFeature> failed = new ArrayList<>(intents.size());
    for (final Intent intent : intents) {
      failed.add(
          new PerFeature(
              intent.feature(),
              ApplyStatus.FAILED,
              FeatureResult.failure(
                  FailureCode.INTERNAL,
                  "backend does not implement the "
                      + aggregation
                      + " mix-in for feature '"
                      + intent.feature().name()
                      + "'")));
    }
    return failed;
  }

  /**
   * Queries a single feature's value over each of its windows for a subject.
   *
   * @param namespace the tenancy scope
   * @param subject the subject to read
   * @param featureName the feature to read
   * @return one result per the feature's windows, in declaration order (each stamped with the
   *     current definition version)
   * @throws IllegalArgumentException if the namespace has no such feature
   */
  public List<FeatureResult> query(
      final Namespace namespace, final Subject subject, final String featureName) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(featureName, "featureName");
    final FeatureDefinitions definitions = definitionProvider.definitions(namespace);
    final FeatureDefinition definition =
        definitions
            .definition(featureName)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "no feature '"
                            + featureName
                            + "' in namespace "
                            + namespace.value()
                            + " (version "
                            + definitions.versionHash()
                            + ")"));
    return queryDefinition(namespace, subject, definition, definitions.versionHash());
  }

  /**
   * Queries several features for a subject in one call.
   *
   * @param namespace the tenancy scope
   * @param subject the subject to read
   * @param featureNames the features to read
   * @return a map of feature name → per-window results, in the request's iteration order
   * @throws IllegalArgumentException if the namespace has no such feature
   */
  public Map<String, List<FeatureResult>> query(
      final Namespace namespace, final Subject subject, final List<String> featureNames) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(featureNames, "featureNames");
    final Map<String, List<FeatureResult>> results = new LinkedHashMap<>();
    for (final String featureName : featureNames) {
      results.put(featureName, query(namespace, subject, featureName));
    }
    return results;
  }

  private List<FeatureResult> queryDefinition(
      final Namespace namespace,
      final Subject subject,
      final FeatureDefinition definition,
      final String versionHash) {
    final VelocityBackend backend = backends.backend(definition.backend());
    final Aggregation aggregation = definition.aggregation();
    final List<QueryTuple> tuples = new ArrayList<>(definition.windows().size());
    for (final Window window : definition.windows()) {
      tuples.add(new QueryTuple(subject, aggregation, window));
    }
    final QueryContext ctx = new QueryContext(namespace, null);
    final List<FeatureResult> raw =
        switch (aggregation.type()) {
          case COUNT ->
              backend instanceof CountStore store
                  ? store.queryCount(ctx, tuples)
                  : queryUnsupported(tuples, "COUNT");
          case SUM ->
              backend instanceof SumStore store
                  ? store.querySum(ctx, tuples)
                  : queryUnsupported(tuples, "SUM");
          case DISTINCT ->
              backend instanceof DistinctStore store
                  ? store.queryDistinct(ctx, tuples)
                  : queryUnsupported(tuples, "DISTINCT");
        };
    return stampResults(raw, versionHash);
  }

  private static List<FeatureResult> queryUnsupported(
      final List<QueryTuple> tuples, final String aggregation) {
    final List<FeatureResult> failed = new ArrayList<>(tuples.size());
    for (int i = 0; i < tuples.size(); i++) {
      failed.add(
          FeatureResult.failure(
              FailureCode.INTERNAL, "backend does not implement the " + aggregation + " mix-in"));
    }
    return failed;
  }

  /**
   * The declared capabilities of a registered backend (passthrough).
   *
   * @param backendName the backend name
   * @return the backend's capabilities
   */
  public BackendCapabilities capabilities(final String backendName) {
    Objects.requireNonNull(backendName, "backendName");
    return backends.backend(backendName).capabilities();
  }

  /**
   * Administratively purges stored state on every registered backend (FR-23).
   *
   * @param namespace the namespace to erase within
   * @param subject the subject to erase; null erases the whole namespace
   */
  public void purge(final Namespace namespace, final @Nullable Subject subject) {
    Objects.requireNonNull(namespace, "namespace");
    for (final String name : backends.names()) {
      backends.backend(name).purge(namespace, subject);
    }
  }

  /**
   * Validates a candidate definition set against its target backends' capabilities (FR-29) without
   * installing it.
   *
   * @param definitions the candidate definitions
   * @return the collected violations (empty when the set is valid)
   */
  public CapabilityValidationResult validateReload(final List<FeatureDefinition> definitions) {
    Objects.requireNonNull(definitions, "definitions");
    final List<Violation> violations = new ArrayList<>();
    for (final FeatureDefinition definition : definitions) {
      final VelocityBackend backend =
          backends.names().contains(definition.backend())
              ? backends.backend(definition.backend())
              : null;
      if (backend == null) {
        violations.add(
            new Violation(
                definition.name(),
                "unknown backend '"
                    + definition.backend()
                    + "'; known backends: "
                    + backends.names()));
        continue;
      }
      violations.addAll(
          capabilityValidator.validate(definition, backend.capabilities()).violations());
    }
    return new CapabilityValidationResult(violations);
  }

  /**
   * Validates and atomically installs a new definition set for a namespace (FR-17/FR-29).
   *
   * <p>Every definition is validated against its target backend's capabilities first; if any is
   * invalid the whole reload is rejected and nothing is installed (atomic). On success the
   * namespace's snapshot is swapped atomically and its version hash recomputed (FR-40).
   *
   * @param namespace the namespace to reload
   * @param definitions the new definition set
   * @return the newly installed snapshot
   * @throws IllegalArgumentException if any definition is invalid (the message lists every
   *     violation)
   * @throws UnsupportedOperationException if the configured provider does not support reload
   */
  public FeatureDefinitions reload(
      final Namespace namespace, final List<FeatureDefinition> definitions) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(definitions, "definitions");
    if (!(definitionProvider instanceof MutableFeatureDefinitionProvider mutable)) {
      throw new UnsupportedOperationException(
          "reload requires a MutableFeatureDefinitionProvider; configured provider is "
              + definitionProvider.getClass().getName());
    }
    final CapabilityValidationResult validation = validateReload(definitions);
    if (!validation.valid()) {
      final StringBuilder message = new StringBuilder("reload rejected for namespace ");
      message.append(namespace.value()).append(':');
      for (final Violation violation : validation.violations()) {
        message
            .append("\n  - ")
            .append(violation.feature())
            .append(": ")
            .append(violation.message());
      }
      throw new IllegalArgumentException(message.toString());
    }
    return mutable.reload(namespace, definitions);
  }

  private static List<PerFeature> stampAll(
      final List<PerFeature> outcomes, final String versionHash) {
    final List<PerFeature> stamped = new ArrayList<>(outcomes.size());
    for (final PerFeature outcome : outcomes) {
      stamped.add(
          new PerFeature(
              outcome.feature(), outcome.status(), stamp(outcome.result(), versionHash)));
    }
    return stamped;
  }

  private static List<FeatureResult> stampResults(
      final List<FeatureResult> results, final String versionHash) {
    final List<FeatureResult> stamped = new ArrayList<>(results.size());
    for (final FeatureResult result : results) {
      stamped.add(stamp(result, versionHash));
    }
    return stamped;
  }

  private static FeatureResult stamp(final FeatureResult result, final String versionHash) {
    if (result instanceof FeatureResult.Success success) {
      final FeatureValue value = success.value();
      return FeatureResult.success(
          new FeatureValue(
              value.feature(),
              value.window(),
              value.value(),
              value.exactness(),
              value.readYourWriteLevel(),
              versionHash,
              value.windowBounds(),
              value.asOf()));
    }
    return result;
  }
}
