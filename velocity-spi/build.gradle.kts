plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-spi: the core↔backend contract — capability mix-ins (CountStore, SumStore," +
        " DistinctStore, Sliding/TumblingSupport, SeedSupport), BackendCapabilities, and shared SPI DTOs."

dependencies {
    // jspecify nullability annotations are part of the published compatibility surface (NFR-17),
    // so they are `api`, not `compileOnly` as in the baseline java-conventions.
    api(libs.jspecify)
    // SPI DTOs (intents, feature values) serialize with Jackson 3 on the wire (NFR-4).
    api(libs.bundles.jackson)

    testRuntimeOnly(libs.logback.classic)
}
