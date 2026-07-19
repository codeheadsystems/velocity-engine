// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.resource;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/** The exception mapper's status mapping (AR-5 problem model). */
final class VelocityExceptionMapperTest {

  private final VelocityExceptionMapper mapper = new VelocityExceptionMapper();

  @Test
  void webApplicationExceptionKeepsItsStatus() {
    try (Response response = mapper.toResponse(new NotFoundException())) {
      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Test
  void illegalArgumentMapsToBadRequest() {
    try (Response response = mapper.toResponse(new IllegalArgumentException("bad input"))) {
      assertThat(response.getStatus()).isEqualTo(400);
    }
  }

  @Test
  void otherRuntimeMapsToInternalServerError() {
    try (Response response = mapper.toResponse(new IllegalStateException("boom"))) {
      assertThat(response.getStatus()).isEqualTo(500);
    }
  }
}
