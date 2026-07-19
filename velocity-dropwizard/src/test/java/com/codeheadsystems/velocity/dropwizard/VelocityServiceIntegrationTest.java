// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.dropwizard.api.Wire;
import com.codeheadsystems.velocity.dropwizard.auth.ApiKeyFilter;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** End-to-end HTTP tests: the service over the in-memory engine, behind namespace-scoped auth. */
@ExtendWith(DropwizardExtensionsSupport.class)
final class VelocityServiceIntegrationTest {

  private static final DropwizardAppExtension<VelocityConfiguration> APP =
      new DropwizardAppExtension<>(
          TestApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

  private static String url(final String path) {
    return "http://localhost:" + APP.getLocalPort() + path;
  }

  @Test
  void recordThenQueryRoundTripOverHttp() {
    final Wire.RecordResponse recorded =
        APP.client()
            .target(url("/v1/acme/record"))
            .request()
            .header(ApiKeyFilter.HEADER, "key-acme")
            .post(
                Entity.json(
                    new Wire.RecordRequest(new Wire.SubjectDto("card", "1234"), Map.of(), "14950")),
                Wire.RecordResponse.class);
    assertThat(recorded.perFeature()).isNotEmpty();

    final Wire.QueryResponse queried =
        APP.client()
            .target(url("/v1/acme/query"))
            .request()
            .header(ApiKeyFilter.HEADER, "key-acme")
            .post(
                Entity.json(
                    new Wire.QueryRequest(
                        new Wire.SubjectDto("card", "1234"), List.of("card.count", "card.sum"))),
                Wire.QueryResponse.class);

    // COUNT ignored the money value; SUM took it — money round-trips as a decimal-integer string.
    assertThat(successValue(queried, "card.count")).isEqualTo("1");
    assertThat(successValue(queried, "card.sum")).isEqualTo("14950");
  }

  @Test
  void missingApiKeyIsUnauthorized() {
    try (Response response = APP.client().target(url("/v1/acme/capabilities")).request().get()) {
      assertThat(response.getStatus()).isEqualTo(401);
    }
  }

  @Test
  void keyScopedToAnotherNamespaceIsForbidden() {
    try (Response response =
        APP.client()
            .target(url("/v1/acme/capabilities"))
            .request()
            .header(ApiKeyFilter.HEADER, "key-other")
            .get()) {
      assertThat(response.getStatus()).isEqualTo(403);
    }
  }

  @Test
  void capabilitiesReportsDeclaredWindowsAndAggregations() {
    final Wire.CapabilitiesResponse caps =
        APP.client()
            .target(url("/v1/acme/capabilities"))
            .request()
            .header(ApiKeyFilter.HEADER, "key-acme")
            .get(Wire.CapabilitiesResponse.class);
    assertThat(caps.aggregations()).contains("COUNT", "SUM", "DISTINCT");
    assertThat(caps.windows()).isNotEmpty();
    assertThat(caps.readYourWriteLevel()).isEqualTo("ATOMIC");
  }

  private static String successValue(final Wire.QueryResponse response, final String feature) {
    final List<Wire.ResultDto> results = response.features().get(feature);
    assertThat(results).as("results for %s", feature).isNotEmpty();
    final Wire.ResultDto first = results.get(0);
    assertThat(first.kind()).isEqualTo("SUCCESS");
    assertThat(first.value()).isNotNull();
    return first.value().value();
  }
}
