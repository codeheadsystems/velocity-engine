// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.FeatureDefinitions;
import com.codeheadsystems.velocity.spi.model.Namespace;

/**
 * Supplies the current {@link FeatureDefinitions} snapshot for a namespace (FR-16/FR-17).
 *
 * <p>The engine reads a whole snapshot per request. Hot-reload is expressed as an atomic swap of
 * the snapshot behind this interface, so a reader never observes a half-applied definition set
 * (FR-17). A namespace with no configured definitions returns an empty snapshot, not {@code null}
 * (FR-4).
 */
public interface FeatureDefinitionProvider {

  /**
   * The current snapshot for a namespace.
   *
   * @param namespace the namespace
   * @return the current snapshot; empty (never null) for an unconfigured namespace
   */
  FeatureDefinitions definitions(Namespace namespace);
}
