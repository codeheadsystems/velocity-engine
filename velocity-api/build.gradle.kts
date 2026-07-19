plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-api: the OpenAPI 3.1 document (source of truth); API DTOs are generated from it" +
        " downstream (openapi-generator, AR-3)."

dependencies {
    // No hand-written DTOs live here — the yaml under src/main/resources/openapi is the source of
    // truth and clients/DTOs are generated from it downstream (AR-3). The only dependency is the
    // OpenAPI parser used by OpenApiSpecTest to prove the committed spec is valid OpenAPI 3.1.
    testImplementation(libs.swagger.parser)

    testRuntimeOnly(libs.logback.classic)
}
