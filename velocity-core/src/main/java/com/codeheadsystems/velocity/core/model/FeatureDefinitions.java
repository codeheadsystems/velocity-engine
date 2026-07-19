// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model;

import com.codeheadsystems.velocity.spi.model.Namespace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, namespace-scoped snapshot of a namespace's feature definitions (FR-17), stamped
 * with a deterministic {@code versionHash} (FR-40).
 *
 * <p>Hot-reload swaps a whole {@code FeatureDefinitions} atomically (FR-17): a reader either sees
 * the old snapshot in full or the new one in full, never a half-applied set. The {@code
 * versionHash} is the value stamped onto every {@link
 * com.codeheadsystems.velocity.spi.model.FeatureValue} the snapshot produces, so a caller can
 * detect that a definition changed under a running rule (FR-40).
 *
 * @param namespace the namespace this snapshot is for
 * @param definitions the definitions in this snapshot; defensively copied, feature names must be
 *     unique
 * @param versionHash the deterministic version stamp computed from {@code definitions} (FR-40)
 */
public record FeatureDefinitions(
    Namespace namespace, List<FeatureDefinition> definitions, String versionHash) {

  /** Validates uniqueness of feature names and stores an unmodifiable copy of the definitions. */
  public FeatureDefinitions {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(definitions, "definitions");
    Objects.requireNonNull(versionHash, "versionHash");
    definitions = List.copyOf(definitions);
    final Map<String, FeatureDefinition> byName = new LinkedHashMap<>();
    for (final FeatureDefinition definition : definitions) {
      if (byName.putIfAbsent(definition.name(), definition) != null) {
        throw new IllegalArgumentException(
            "duplicate feature name '" + definition.name() + "' in namespace " + namespace.value());
      }
    }
  }

  /**
   * Looks up a definition by feature name.
   *
   * @param featureName the feature name
   * @return the matching definition, or empty if this snapshot has no such feature
   */
  public Optional<FeatureDefinition> definition(final String featureName) {
    for (final FeatureDefinition definition : definitions) {
      if (definition.name().equals(featureName)) {
        return Optional.of(definition);
      }
    }
    return Optional.empty();
  }

  /**
   * Whether this snapshot has no definitions (e.g. an unconfigured namespace).
   *
   * @return {@code true} if there are no definitions
   */
  public boolean isEmpty() {
    return definitions.isEmpty();
  }
}
