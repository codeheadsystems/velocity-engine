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

    // NOTE: velocity-spi is deliberately SERIALIZATION-NEUTRAL (ADR 0002, ADR 0009). The SPI DTOs
    // (intents, FeatureResult/FeatureValue, BackendCapabilities) are plain records with NO Jackson
    // binding, so a third-party backend author does not inherit the engine's serialization stack on
    // their classpath as a frozen part of the contract. Jackson 3 lives in velocity-core / the wire
    // layer, which owns JSON (NFR-4). Do not add Jackson to this module's `api` surface.

    testRuntimeOnly(libs.logback.classic)
}
