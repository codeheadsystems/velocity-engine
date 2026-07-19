plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-backend-redis: the v1 Redis/Lettuce sliding hot-path reference backend — an exact," +
        " atomic, read-your-write VelocityBackend (COUNT/SUM/DISTINCT over true sliding windows via" +
        " sorted sets + Lua) that passes the velocity-testkit conformance TCK against real Redis."

// Lettuce ships as an automatic module and pulls Reactor/Netty (also automatic modules); the
// baseline -Xlint:all makes their `requires` a warning, which -Werror (library-conventions) turns
// fatal. Silence just the automatic-module lints on both the main and test compiles (mirrors the
// jdbi module).
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(
        listOf("-Xlint:-requires-automatic", "-Xlint:-requires-transitive-automatic"),
    )
}
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(
        listOf("-Xlint:-requires-automatic", "-Xlint:-requires-transitive-automatic"),
    )
}

dependencies {
    // The frozen contract this backend implements is on the api surface.
    api(project(":velocity-spi"))
    api(libs.jspecify)

    implementation(libs.lettuce.core)
    implementation(libs.slf4j.api)

    // Reactor's class files reference com.google.errorprone.annotations.*; without errorprone
    // annotations on the compile classpath javac emits an annotation-not-found warning that -Werror
    // turns fatal. compileOnly so it is not broadcast as a runtime dependency.
    compileOnly(libs.build.errorprone.annotations)
    testCompileOnly(libs.build.errorprone.annotations)

    // The testkit drives the conformance TCK (*Scenarios) + MutableClock against this backend.
    testImplementation(project(":velocity-testkit"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.logback.classic)
}
