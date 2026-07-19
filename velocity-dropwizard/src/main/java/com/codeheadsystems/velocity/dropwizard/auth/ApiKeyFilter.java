// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.auth;

import com.codeheadsystems.velocity.dropwizard.api.Wire.Problem;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Namespace-scoped API-key authentication (P9, NFR-21). Reads {@code X-API-Key}, rejects a missing
 * or unknown key with 401, and rejects a request whose path {@code {namespace}} is outside the
 * key's allowed set with 403 — so a credential can never touch another tenant's counts. Errors use
 * the RFC 9457 problem model (AR-5).
 */
@Provider
public final class ApiKeyFilter implements ContainerRequestFilter {

  /** The header carrying the API key. */
  public static final String HEADER = "X-API-Key";

  private final Map<String, Set<String>> keyToNamespaces;

  /**
   * @param keyToNamespaces each API key mapped to the namespaces it may access.
   */
  public ApiKeyFilter(final Map<String, Set<String>> keyToNamespaces) {
    this.keyToNamespaces = Map.copyOf(keyToNamespaces);
  }

  @Override
  public void filter(final ContainerRequestContext requestContext) {
    final String key = requestContext.getHeaderString(HEADER);
    if (key == null || key.isBlank()) {
      abort(requestContext, Response.Status.UNAUTHORIZED, "unauthenticated", "missing " + HEADER);
      return;
    }
    final Set<String> allowed = keyToNamespaces.get(key);
    if (allowed == null) {
      abort(requestContext, Response.Status.UNAUTHORIZED, "unauthenticated", "unknown API key");
      return;
    }
    final @Nullable String namespace =
        requestContext.getUriInfo().getPathParameters().getFirst("namespace");
    if (namespace == null || !allowed.contains(namespace)) {
      abort(
          requestContext,
          Response.Status.FORBIDDEN,
          "forbidden-namespace",
          "API key is not authorized for namespace '" + namespace + "'");
    }
  }

  private static void abort(
      final ContainerRequestContext requestContext,
      final Response.Status status,
      final String type,
      final String detail) {
    requestContext.abortWith(
        Response.status(status)
            .type(MediaType.APPLICATION_JSON)
            .entity(new Problem("about:blank#" + type, type, status.getStatusCode(), detail))
            .build());
  }
}
