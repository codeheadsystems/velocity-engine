// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.net.URL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the committed OpenAPI document is valid OpenAPI 3.1 and encodes the contract the rest of
 * the system generates against (AR-1/AR-3). If this fails, downstream client generation is building
 * on a broken source of truth — so the assertions below would fail if the spec were malformed or an
 * endpoint/schema were dropped.
 */
class OpenApiSpecTest {

  private static final String SPEC_CLASSPATH = "openapi/velocity-engine-api.yaml";
  private static final String MONEY_STRING_PATTERN = "^-?\\d+$";

  private static SwaggerParseResult result;
  private static OpenAPI openapi;

  @BeforeAll
  static void parseSpec() {
    URL specUrl = OpenApiSpecTest.class.getClassLoader().getResource(SPEC_CLASSPATH);
    assertThat(specUrl).as("spec must be on the classpath at %s", SPEC_CLASSPATH).isNotNull();
    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    result = new OpenAPIV3Parser().readLocation(specUrl.toString(), null, options);
    openapi = result.getOpenAPI();
  }

  @Test
  void parsesWithNoErrorsOrWarnings() {
    // A nonempty messages list means the parser found problems (unresolved $ref, bad schema, …).
    assertThat(result.getMessages()).isEmpty();
    assertThat(openapi).isNotNull();
  }

  @Test
  void isOpenApi31() {
    assertThat(openapi.getOpenapi()).startsWith("3.1");
  }

  @Test
  void declaresAllFiveEndpoints() {
    assertThat(openapi.getPaths())
        .containsKeys(
            "/v1/{namespace}/record",
            "/v1/{namespace}/query",
            "/v1/{namespace}/capabilities",
            "/v1/{namespace}/features",
            "/v1/{namespace}/purge");
  }

  @Test
  void recordAndQueryArePosts() {
    assertThat(operation("/v1/{namespace}/record")).isNotNull();
    assertThat(openapi.getPaths().get("/v1/{namespace}/record").getPost()).isNotNull();
    assertThat(openapi.getPaths().get("/v1/{namespace}/query").getPost()).isNotNull();
    assertThat(openapi.getPaths().get("/v1/{namespace}/capabilities").getGet()).isNotNull();
    assertThat(openapi.getPaths().get("/v1/{namespace}/features").getGet()).isNotNull();
    assertThat(openapi.getPaths().get("/v1/{namespace}/purge").getPost()).isNotNull();
  }

  @Test
  void moneyAndValueAreIntegerStrings() {
    Schema<?> money = schema("Money");
    Schema<?> value = schema("Value");
    assertThat(typeOf(money)).isEqualTo("string");
    assertThat(money.getPattern()).isEqualTo(MONEY_STRING_PATTERN);
    assertThat(typeOf(value)).isEqualTo("string");
    assertThat(value.getPattern()).isEqualTo(MONEY_STRING_PATTERN);
  }

  @Test
  void featureResultIsADiscriminatedOneOf() {
    Schema<?> featureResult = schema("FeatureResult");
    assertThat(featureResult.getOneOf())
        .as("FeatureResult must be a value-or-failure oneOf (ADR 0009)")
        .hasSize(2);
    assertThat(featureResult.getDiscriminator()).isNotNull();
    assertThat(featureResult.getDiscriminator().getPropertyName()).isEqualTo("kind");
  }

  @Test
  void topLevelSchemasArePresent() {
    assertThat(openapi.getComponents().getSchemas())
        .containsKeys(
            "Subject",
            "Aggregation",
            "Window",
            "WindowBounds",
            "Money",
            "Value",
            "FeatureValue",
            "FeatureResult",
            "PerFeature",
            "ApplyResult",
            "QueryRequest",
            "QueryResponse",
            "BackendCapabilities",
            "FeatureDefinition",
            "Problem");
  }

  @Test
  void apiKeySecuritySchemeIsDeclaredAndGlobal() {
    SecurityScheme scheme = openapi.getComponents().getSecuritySchemes().get("ApiKeyAuth");
    assertThat(scheme).isNotNull();
    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(scheme.getIn()).isEqualTo(SecurityScheme.In.HEADER);
    assertThat(scheme.getName()).isEqualTo("X-API-Key");
    // Applied globally (P9): the root security requirement references the scheme.
    assertThat(openapi.getSecurity()).anySatisfy(req -> assertThat(req).containsKey("ApiKeyAuth"));
  }

  @Test
  void errorsUseProblemJson() {
    // AR-5: error responses are RFC 9457 application/problem+json.
    var validation = openapi.getComponents().getResponses().get("Validation");
    assertThat(validation).isNotNull();
    assertThat(validation.getContent()).containsKey("application/problem+json");
  }

  private static PathItem operation(String path) {
    return openapi.getPaths().get(path);
  }

  /**
   * The single JSON type of a schema. OpenAPI 3.1 (JSON Schema 2020-12) parses {@code type} into a
   * {@code types} set, leaving {@code getType()} null, so read whichever the parser populated.
   */
  private static String typeOf(Schema<?> schema) {
    if (schema.getType() != null) {
      return schema.getType();
    }
    assertThat(schema.getTypes()).hasSize(1);
    return schema.getTypes().iterator().next();
  }

  private static Schema<?> schema(String name) {
    Schema<?> schema = openapi.getComponents().getSchemas().get(name);
    assertThat(schema).as("schema %s must exist", name).isNotNull();
    return schema;
  }
}
