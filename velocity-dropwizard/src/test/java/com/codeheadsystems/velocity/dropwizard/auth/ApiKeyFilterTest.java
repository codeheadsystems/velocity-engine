// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Namespace-scoped API-key filter branches (NFR-21). */
final class ApiKeyFilterTest {

  private final ApiKeyFilter filter = new ApiKeyFilter(Map.of("key-acme", Set.of("acme")));

  private static ContainerRequestContext request(final String apiKey, final String namespace) {
    final ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getHeaderString(ApiKeyFilter.HEADER)).thenReturn(apiKey);
    final UriInfo uriInfo = mock(UriInfo.class);
    final MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
    if (namespace != null) {
      pathParams.putSingle("namespace", namespace);
    }
    lenient().when(uriInfo.getPathParameters()).thenReturn(pathParams);
    lenient().when(ctx.getUriInfo()).thenReturn(uriInfo);
    return ctx;
  }

  private static int abortStatus(final ContainerRequestContext ctx) {
    final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(ctx).abortWith(captor.capture());
    return captor.getValue().getStatus();
  }

  @Test
  void missingKeyIsUnauthorized() {
    final ContainerRequestContext ctx = request(null, "acme");
    filter.filter(ctx);
    assertThat(abortStatus(ctx)).isEqualTo(401);
  }

  @Test
  void blankKeyIsUnauthorized() {
    final ContainerRequestContext ctx = request("  ", "acme");
    filter.filter(ctx);
    assertThat(abortStatus(ctx)).isEqualTo(401);
  }

  @Test
  void unknownKeyIsUnauthorized() {
    final ContainerRequestContext ctx = request("nope", "acme");
    filter.filter(ctx);
    assertThat(abortStatus(ctx)).isEqualTo(401);
  }

  @Test
  void keyScopedToAnotherNamespaceIsForbidden() {
    final ContainerRequestContext ctx = request("key-acme", "other");
    filter.filter(ctx);
    assertThat(abortStatus(ctx)).isEqualTo(403);
  }

  @Test
  void authorizedKeyPasses() {
    final ContainerRequestContext ctx = request("key-acme", "acme");
    filter.filter(ctx);
    verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
  }
}
