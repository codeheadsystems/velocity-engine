// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.spi.VelocityBackend;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable registry of the backends the engine can dispatch to, keyed by the backend name a
 * feature definition binds to (FR-16).
 *
 * <p>Injected at engine construction (no Dagger here — DI lives in the service tier). A feature
 * bound to an unknown backend is a configuration error surfaced immediately by {@link #backend}.
 */
public final class BackendRegistry {

  private final Map<String, VelocityBackend> backends;

  /**
   * Creates a registry from a name → backend map.
   *
   * @param backends the backends by name; copied defensively, must be non-empty with non-blank keys
   */
  public BackendRegistry(final Map<String, VelocityBackend> backends) {
    Objects.requireNonNull(backends, "backends");
    if (backends.isEmpty()) {
      throw new IllegalArgumentException("backend registry must not be empty");
    }
    backends.forEach(
        (name, backend) -> {
          Objects.requireNonNull(name, "backend name");
          Objects.requireNonNull(backend, "backend for '" + name + "'");
          if (name.isBlank()) {
            throw new IllegalArgumentException("backend name must not be blank");
          }
        });
    this.backends = Map.copyOf(backends);
  }

  /**
   * Looks up a backend by name.
   *
   * @param name the backend name a definition binds to
   * @return the backend
   * @throws IllegalArgumentException if no backend is registered under {@code name}
   */
  public VelocityBackend backend(final String name) {
    Objects.requireNonNull(name, "name");
    final VelocityBackend backend = backends.get(name);
    if (backend == null) {
      throw new IllegalArgumentException(
          "no backend registered under name '" + name + "'; known backends: " + backends.keySet());
    }
    return backend;
  }

  /**
   * The registered backend names.
   *
   * @return the immutable set of names
   */
  public Set<String> names() {
    return backends.keySet();
  }
}
