// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.dagger;

import com.codeheadsystems.velocity.core.VelocityEngine;
import com.codeheadsystems.velocity.dropwizard.auth.ApiKeyFilter;
import com.codeheadsystems.velocity.dropwizard.resource.VelocityResource;
import dagger.Component;
import jakarta.inject.Singleton;

/** The Dagger graph the application resolves the JAX-RS resource, auth filter, and engine from. */
@Singleton
@Component(modules = VelocityModule.class)
public interface VelocityComponent {

  /**
   * Returns the velocity JAX-RS resource.
   *
   * @return the velocity JAX-RS resource.
   */
  VelocityResource resource();

  /**
   * Returns the API-key auth filter.
   *
   * @return the API-key auth filter.
   */
  ApiKeyFilter apiKeyFilter();

  /**
   * Returns the engine, exposed so the application can seed feature definitions at startup.
   *
   * @return the engine.
   */
  VelocityEngine engine();
}
