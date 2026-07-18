// Root build for velocity-engine. Convention plugins live in build-logic/ and are applied
// per-module. The `base` plugin gives the root project the standard lifecycle tasks (build, check,
// clean, assemble).
plugins {
    base
}

// Required for the nmcp aggregation plugin (auto-applied by `nmcp.settings` in settings.gradle.kts)
// to resolve its runtime dependencies. Subprojects keep their own repository declarations.
repositories {
    mavenCentral()
    gradlePluginPortal()
}

// `test` is a lifecycle task at the root so `./gradlew clean build test` aggregates `test`
// across all subprojects. Gradle's multi-project task expansion runs each subproject's own
// `test` task automatically; this root task is the aggregating entry point.
tasks.register("test") {
    group = "verification"
    description = "Lifecycle task that aggregates `test` across all subprojects."
    dependsOn(subprojects.map { it.tasks.matching { t -> t.name == "test" } })
}
