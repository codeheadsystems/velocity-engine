plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-core: the embeddable engine — record()/query(), fan-out resolver, window/aggregation" +
        " model, and feature-definition YAML I/O (depends on velocity-spi)."

dependencies {
    // The engine resolves fan-out to backend-neutral intents defined in velocity-spi (§6.1 R1).
    api(project(":velocity-spi"))
    api(libs.jspecify)
    api(libs.bundles.jackson)
    // Feature definitions import/export as YAML (FR-28); event records import as YAML or JSON (FR-30).
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.caffeine)
    implementation(libs.slf4j.api)

    // Optional: the service tier can wire a real MeterRegistry (NFR-11). The core falls back to a
    // no-op when Micrometer isn't on the runtime classpath.
    compileOnly(libs.micrometer.core)

    testImplementation(project(":velocity-testkit"))
    testImplementation(libs.micrometer.core)
    testRuntimeOnly(libs.logback.classic)
}

// velocity.test-conventions sets the baseline gate (LINE ≥0.70, BRANCH ≥0.55) for every library
// module. velocity-core is the engine and is held to a stricter ≥80% line bar (NFR-14); the
// baseline branch floor applies via the convention.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
