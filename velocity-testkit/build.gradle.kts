plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-testkit: an in-memory velocity-spi implementation plus the mandatory SPI conformance" +
        " TCK (*Scenarios) and fixtures every velocity-backend-* must pass (NFR-13, NFR-18)."

dependencies {
    // The in-memory backend implements velocity-spi, and the conformance scenarios drive the SPI
    // contract; both are on the api surface so backend modules inherit them from their tests.
    api(project(":velocity-spi"))
    api(libs.jspecify)
    // AssertJ is on the api surface because the *Scenarios TCK asserts with `assertThat`; backend
    // modules drive these scenarios from their integration tests and inherit the dependency.
    api(libs.assertj.core)

    testRuntimeOnly(libs.logback.classic)
}
