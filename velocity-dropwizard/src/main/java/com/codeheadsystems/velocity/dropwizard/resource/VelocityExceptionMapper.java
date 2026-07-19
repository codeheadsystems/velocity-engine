// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.resource;

import com.codeheadsystems.velocity.dropwizard.api.Wire.Problem;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps engine/JAX-RS exceptions to the RFC 9457 problem model (AR-5). A JAX-RS {@link
 * WebApplicationException} keeps its own status; an {@link IllegalArgumentException} (validation /
 * unknown backend or namespace) maps to 400; anything else is a 500.
 */
@Provider
public final class VelocityExceptionMapper implements ExceptionMapper<RuntimeException> {

  @Override
  public Response toResponse(final RuntimeException exception) {
    if (exception instanceof WebApplicationException webException) {
      return webException.getResponse();
    }
    final Response.Status status =
        exception instanceof IllegalArgumentException
            ? Response.Status.BAD_REQUEST
            : Response.Status.INTERNAL_SERVER_ERROR;
    final String type = status == Response.Status.BAD_REQUEST ? "validation" : "internal";
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(
            new Problem(
                "about:blank#" + type, type, status.getStatusCode(), exception.getMessage()))
        .build();
  }
}
