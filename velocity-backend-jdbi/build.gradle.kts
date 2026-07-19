plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-backend-jdbi: the v1 Postgres/JDBI tumbling reference backend — an exact, atomic," +
        " read-your-write VelocityBackend (COUNT/SUM/DISTINCT over tumbling windows) that passes the" +
        " velocity-testkit conformance TCK against real Postgres via Testcontainers."

// JDBI, HikariCP, PostgreSQL and Testcontainers ship as automatic modules (no module-info); the
// baseline -Xlint:all makes their `requires` a warning, which -Werror (library-conventions) turns
// fatal. Mirror the pk-auth JDBI module and silence just the automatic-module lints on both the
// main and test compiles.
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

    implementation(libs.jdbi.core)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.slf4j.api)

    // JDBI's class files reference com.google.errorprone.annotations.concurrent.GuardedBy; without
    // errorprone-annotations on the compile classpath javac emits an annotation-not-found warning
    // that -Werror turns fatal. compileOnly so it is not broadcast as a runtime dependency.
    compileOnly(libs.build.errorprone.annotations)
    testCompileOnly(libs.build.errorprone.annotations)

    // The testkit drives the conformance TCK (*Scenarios) + MutableClock against this backend.
    testImplementation(project(":velocity-testkit"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.logback.classic)
}
