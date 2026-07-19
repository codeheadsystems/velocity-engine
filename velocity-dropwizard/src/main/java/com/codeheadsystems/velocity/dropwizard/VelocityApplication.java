// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard;

import com.codeheadsystems.velocity.core.VelocityEngine;
import com.codeheadsystems.velocity.dropwizard.dagger.DaggerVelocityComponent;
import com.codeheadsystems.velocity.dropwizard.dagger.VelocityComponent;
import com.codeheadsystems.velocity.dropwizard.dagger.VelocityModule;
import com.codeheadsystems.velocity.dropwizard.resource.VelocityExceptionMapper;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Dropwizard 5 service. Builds the Dagger graph, seeds feature definitions, and registers the
 * JAX-RS resource behind the {@link com.codeheadsystems.velocity.dropwizard.auth.ApiKeyFilter
 * API-key filter} and the {@link VelocityExceptionMapper problem exception mapper}.
 *
 * <p>The concrete backend is supplied by {@link #createBackend(VelocityConfiguration)} — override
 * it (or a subclass wiring a {@code velocity-backend-*} module) to run the service; the default
 * throws so a misconfigured deployment fails fast rather than silently serving nothing.
 */
public class VelocityApplication extends Application<VelocityConfiguration> {

  /**
   * Entry point: starts the service from the given arguments.
   *
   * @param args CLI arguments.
   * @throws Exception if the service fails to start.
   */
  public static void main(final String[] args) throws Exception {
    new VelocityApplication().run(args);
  }

  @Override
  public String getName() {
    return "velocity-engine";
  }

  @Override
  public void run(final VelocityConfiguration configuration, final Environment environment) {
    final VelocityBackend backend = createBackend(configuration);
    final VelocityComponent component =
        DaggerVelocityComponent.builder()
            .velocityModule(
                new VelocityModule(
                    backend, configuration.getBackendName(), apiKeySets(configuration)))
            .build();
    configureEngine(component.engine(), configuration);
    environment.jersey().register(component.apiKeyFilter());
    environment.jersey().register(component.resource());
    environment.jersey().register(new VelocityExceptionMapper());
  }

  /**
   * Supplies the backend the engine routes to. The default is unconfigured.
   *
   * @param configuration the service configuration.
   * @return the backend.
   */
  protected VelocityBackend createBackend(final VelocityConfiguration configuration) {
    throw new UnsupportedOperationException(
        "no backend configured: override createBackend(...) or wire a velocity-backend-* module");
  }

  /**
   * Seeds feature definitions at startup; the default is a no-op.
   *
   * @param engine the engine to seed.
   * @param configuration the service configuration.
   */
  protected void configureEngine(
      final VelocityEngine engine, final VelocityConfiguration configuration) {
    // Default: no feature definitions. A deployment overrides this (or loads YAML) to define them.
  }

  private static Map<String, Set<String>> apiKeySets(final VelocityConfiguration configuration) {
    final Map<String, Set<String>> keys = new LinkedHashMap<>();
    configuration.getApiKeys().forEach((key, namespaces) -> keys.put(key, Set.copyOf(namespaces)));
    return keys;
  }
}
