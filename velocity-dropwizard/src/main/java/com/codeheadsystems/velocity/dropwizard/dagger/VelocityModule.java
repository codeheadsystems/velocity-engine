// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.dagger;

import com.codeheadsystems.velocity.core.BackendRegistry;
import com.codeheadsystems.velocity.core.CapabilityValidator;
import com.codeheadsystems.velocity.core.DimensionHasher;
import com.codeheadsystems.velocity.core.FeatureDefinitionProvider;
import com.codeheadsystems.velocity.core.InMemoryNamespaceSaltProvider;
import com.codeheadsystems.velocity.core.MutableFeatureDefinitionProvider;
import com.codeheadsystems.velocity.core.VelocityEngine;
import com.codeheadsystems.velocity.dropwizard.auth.ApiKeyFilter;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Set;

/**
 * Wires the {@link VelocityEngine} (a single named backend, an in-memory feature-definition
 * provider, a keyed-hash dimension hasher, and a capability validator) and the API-key filter from
 * runtime values supplied by the {@link com.codeheadsystems.velocity.dropwizard.VelocityApplication
 * application}.
 */
@Module
public final class VelocityModule {

  private final VelocityBackend backend;
  private final String backendName;
  private final Map<String, Set<String>> apiKeys;

  /**
   * @param backend the backend the engine routes to.
   * @param backendName the logical name the backend is registered under.
   * @param apiKeys each API key mapped to its allowed namespaces.
   */
  public VelocityModule(
      final VelocityBackend backend,
      final String backendName,
      final Map<String, Set<String>> apiKeys) {
    this.backend = backend;
    this.backendName = backendName;
    this.apiKeys = Map.copyOf(apiKeys);
  }

  @Provides
  @Singleton
  @Named("backendName")
  String backendName() {
    return backendName;
  }

  @Provides
  @Singleton
  BackendRegistry backendRegistry() {
    return new BackendRegistry(Map.of(backendName, backend));
  }

  @Provides
  @Singleton
  FeatureDefinitionProvider featureDefinitionProvider() {
    return new MutableFeatureDefinitionProvider();
  }

  @Provides
  @Singleton
  DimensionHasher dimensionHasher() {
    return new DimensionHasher(new InMemoryNamespaceSaltProvider());
  }

  @Provides
  @Singleton
  CapabilityValidator capabilityValidator() {
    return new CapabilityValidator();
  }

  @Provides
  @Singleton
  VelocityEngine velocityEngine(
      final BackendRegistry backends,
      final FeatureDefinitionProvider provider,
      final DimensionHasher hasher,
      final CapabilityValidator validator) {
    return new VelocityEngine(backends, provider, hasher, validator);
  }

  @Provides
  @Singleton
  ApiKeyFilter apiKeyFilter() {
    return new ApiKeyFilter(apiKeys);
  }
}
