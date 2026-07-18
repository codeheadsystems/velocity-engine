plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-api: the OpenAPI 3.1 document (source of truth for client generation) and the shared" +
        " API DTOs used by the service tier and the generated Java client."

dependencies {
    // Shared API DTOs are Jackson-serialized (NFR-4) and nullability-annotated (NFR-17).
    api(libs.jspecify)
    api(libs.bundles.jackson)

    testRuntimeOnly(libs.logback.classic)
}
