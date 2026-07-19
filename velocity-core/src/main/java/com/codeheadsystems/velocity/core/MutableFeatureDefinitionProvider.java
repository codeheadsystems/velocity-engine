// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.FeatureDefinitions;
import com.codeheadsystems.velocity.spi.model.Namespace;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The default in-memory {@link FeatureDefinitionProvider}: a concurrent map of namespace → snapshot
 * whose per-namespace snapshot is swapped atomically on {@link #reload} (FR-17).
 *
 * <p>Because each namespace's value is a single immutable {@link FeatureDefinitions} reference, a
 * {@link #definitions} reader either sees the old snapshot in full or the new one in full — never a
 * half-applied set — even while a concurrent {@code reload} runs. Loading definitions from an
 * external store or file watcher (a non-primary hot-reload source, OQ-C) is deferred; such a source
 * would drive {@link #reload}.
 */
public final class MutableFeatureDefinitionProvider implements FeatureDefinitionProvider {

  private final ConcurrentMap<Namespace, FeatureDefinitions> snapshots = new ConcurrentHashMap<>();

  /** Creates an empty provider with no definitions configured for any namespace. */
  public MutableFeatureDefinitionProvider() {}

  @Override
  public FeatureDefinitions definitions(final Namespace namespace) {
    Objects.requireNonNull(namespace, "namespace");
    return snapshots.computeIfAbsent(namespace, MutableFeatureDefinitionProvider::emptySnapshot);
  }

  /**
   * Atomically replaces a namespace's definition set with a fresh snapshot (FR-17), recomputing the
   * version hash (FR-40).
   *
   * @param namespace the namespace to reload
   * @param definitions the new definition set (validated by the engine before this is called)
   * @return the newly installed snapshot
   */
  public FeatureDefinitions reload(
      final Namespace namespace, final List<FeatureDefinition> definitions) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(definitions, "definitions");
    final FeatureDefinitions snapshot =
        new FeatureDefinitions(namespace, definitions, DefinitionVersionHasher.hash(definitions));
    snapshots.put(namespace, snapshot);
    return snapshot;
  }

  private static FeatureDefinitions emptySnapshot(final Namespace namespace) {
    return new FeatureDefinitions(namespace, List.of(), DefinitionVersionHasher.hash(List.of()));
  }
}
