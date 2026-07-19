plugins {
    id("velocity.library-conventions")
    id("velocity.test-conventions")
    id("velocity.publish-conventions")
}

description =
    "velocity-dropwizard: the Dropwizard 5 service tier — Jersey resources over the velocity-core" +
        " engine, Dagger 2 wiring, and namespace-scoped API-key auth, serving the velocity-api" +
        " OpenAPI contract."

tasks.named<JavaCompile>("compileJava") {
    // Dropwizard 5 pulls automatic-module dependencies (jersey, jetty, …) whose `requires` -Werror
    // would turn fatal. -Xlint:-processing silences javac's "no processor claimed these annotations"
    // hint — Dagger claims only its own annotations; JAX-RS/Jakarta ones are runtime-processed.
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:-requires-automatic",
            "-Xlint:-requires-transitive-automatic",
            "-Xlint:-processing",
        ),
    )
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:-requires-automatic",
            "-Xlint:-requires-transitive-automatic",
            "-Xlint:-processing",
        ),
    )
}

dependencies {
    api(project(":velocity-core"))
    api(project(":velocity-api"))
    api(libs.dropwizard.core)
    api(libs.dropwizard.auth)
    api(libs.dagger)
    api(libs.jakarta.inject.api)

    // Dropwizard's transitive Jersey/Jakarta packages reference errorprone annotations; without them
    // on the compile classpath javac emits an annotation-not-found warning that -Werror turns fatal.
    compileOnly(libs.build.errorprone.annotations)
    testCompileOnly(libs.build.errorprone.annotations)

    implementation(libs.slf4j.api)

    annotationProcessor(libs.dagger.compiler)
    testAnnotationProcessor(libs.dagger.compiler)

    testImplementation(project(":velocity-testkit"))
    testImplementation(libs.dropwizard.testing)
    testRuntimeOnly(libs.logback.classic)
}

// Dagger-generated code is excluded from both the report and the coverage gate (NFR-14).
tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "com/codeheadsystems/velocity/dropwizard/dagger/Dagger*",
                        "**/*_Factory.class",
                        "**/*_MembersInjector.class",
                        "**/*_Provide*Factory.class",
                    )
                }
            },
        ),
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "com/codeheadsystems/velocity/dropwizard/dagger/Dagger*",
                        "**/*_Factory.class",
                        "**/*_MembersInjector.class",
                        "**/*_Provide*Factory.class",
                    )
                }
            },
        ),
    )
}
