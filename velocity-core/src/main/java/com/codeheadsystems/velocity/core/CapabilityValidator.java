// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.CapabilityValidationResult;
import com.codeheadsystems.velocity.core.model.CapabilityValidationResult.Violation;
import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates feature definitions against their target backend's declared capabilities (FR-29).
 *
 * <p>The engine rejects an import/reload unless every definition is valid (atomic with FR-17); this
 * validator returns <em>all</em> violations rather than throwing on the first, so the rejection can
 * carry a complete, precise list. Fast-reject, not silent degradation (NFR-18): the engine never
 * pretends a backend can serve a window/aggregation/retention it did not declare.
 *
 * <p>Checks, per definition, that: the aggregation is supported; every window is supported (matches
 * a declared {@code WindowSpec}); the backend's {@code maxRetention} covers the largest window
 * (FR-22a); and DISTINCT is only used where DISTINCT is supported.
 */
public final class CapabilityValidator {

  /** Creates a capability validator. */
  public CapabilityValidator() {}

  /**
   * Validates one definition against a backend's capabilities.
   *
   * @param definition the definition to validate
   * @param capabilities the target backend's declared capabilities
   * @return the collected violations (empty when valid)
   */
  public CapabilityValidationResult validate(
      final FeatureDefinition definition, final BackendCapabilities capabilities) {
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(capabilities, "capabilities");
    final List<Violation> violations = new ArrayList<>();
    collect(definition, capabilities, violations);
    return new CapabilityValidationResult(violations);
  }

  private void collect(
      final FeatureDefinition definition,
      final BackendCapabilities capabilities,
      final List<Violation> violations) {
    final AggregationType type = definition.aggregation().type();
    if (!capabilities.supportsAggregation(type)) {
      violations.add(
          new Violation(
              definition.name(),
              "aggregation "
                  + type
                  + " is not supported by backend '"
                  + definition.backend()
                  + "'"));
    }
    for (final Window window : definition.windows()) {
      if (!capabilities.supportsWindow(window)) {
        violations.add(
            new Violation(
                definition.name(),
                "window "
                    + window.type()
                    + " "
                    + window.duration()
                    + " is not supported by backend '"
                    + definition.backend()
                    + "'"));
      }
    }
    final var largest = definition.largestWindow();
    if (largest.compareTo(capabilities.maxRetention()) > 0) {
      violations.add(
          new Violation(
              definition.name(),
              "largest window "
                  + largest
                  + " exceeds backend '"
                  + definition.backend()
                  + "' maxRetention "
                  + capabilities.maxRetention()
                  + " (FR-22a)"));
    }
  }
}
