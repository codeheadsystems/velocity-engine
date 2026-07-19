// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard;

import io.dropwizard.core.Configuration;
import java.util.List;
import java.util.Map;

/**
 * Service configuration. {@code apiKeys} maps each API key to the namespaces it may access
 * (namespace-scoped authz, NFR-21); {@code backendName} is the logical name the backend is
 * registered under (the {@code /capabilities} endpoint reports it).
 */
public final class VelocityConfiguration extends Configuration {

  private String backendName = "default";
  private Map<String, List<String>> apiKeys = Map.of();

  /**
   * Returns the logical backend name.
   *
   * @return the logical backend name.
   */
  public String getBackendName() {
    return backendName;
  }

  /**
   * Sets the logical backend name.
   *
   * @param backendName the logical backend name.
   */
  public void setBackendName(final String backendName) {
    this.backendName = backendName;
  }

  /**
   * Returns the API-key to allowed-namespaces mapping.
   *
   * @return the API-key to allowed-namespace-list mapping.
   */
  public Map<String, List<String>> getApiKeys() {
    return apiKeys;
  }

  /**
   * Sets the API-key to allowed-namespaces mapping.
   *
   * @param apiKeys the API-key to allowed-namespace-list mapping.
   */
  public void setApiKeys(final Map<String, List<String>> apiKeys) {
    this.apiKeys = Map.copyOf(apiKeys);
  }
}
