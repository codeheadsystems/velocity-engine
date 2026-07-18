// E2E conventions for the example demo(s). The runnable reference service (examples:dropwizard-demo,
// phase 2) carries a sibling `e2e/` suite that drives the full record/query HTTP contract against a
// booted instance. This wires that suite into `check` as an OPT-IN task: it runs only when
// VELOCITY_RUN_E2E=1 (or -PrunE2e) is set, so the default local `check` and the CI `build` job stay
// fast. The dedicated CI e2e jobs set the flag.
//
// No phase-1 module applies this plugin; it is provided so the example demo can adopt it unchanged
// when it lands, mirroring the pk-auth build structure.
plugins {
    java
}

// Every demo's service binds the same port, so two e2eTest tasks must never run at once. With
// org.gradle.parallel=true a root-level `VELOCITY_RUN_E2E=1 ./gradlew check` would otherwise run
// sibling demos' suites concurrently and collide on the port. A shared build service with
// maxParallelUsages=1 serializes them across the whole build (registerIfAbsent dedupes by name, so
// all demos share one lock). In CI each demo runs on its own isolated runner, so this lock is a
// no-op there — it only matters for single-machine multi-demo runs.
abstract class E2eServerLock : BuildService<BuildServiceParameters.None>

val e2eServerLock = gradle.sharedServices.registerIfAbsent(
    "velocityE2eServerLock",
    E2eServerLock::class,
) {
    maxParallelUsages.set(1)
}

// Opt-in switch: VELOCITY_RUN_E2E=1 in the environment, or -PrunE2e on the command line. An
// explicitly set VELOCITY_RUN_E2E (even to a non-"1" value) wins over the property, so
// `VELOCITY_RUN_E2E=0` forces off. Resolved to a plain Boolean at configuration time (the env var /
// property become configuration cache inputs) so the wiring below captures no script reference — an
// `onlyIf { provider.get() }` closure would not be configuration-cache-serializable.
val runE2e: Boolean = providers.environmentVariable("VELOCITY_RUN_E2E").orNull
    ?.let { it == "1" }
    ?: providers.gradleProperty("runE2e").isPresent

val e2eDir = layout.projectDirectory.dir("e2e")

val e2eTest = tasks.register<Exec>("e2eTest") {
    group = "verification"
    description = "Runs the end-to-end suite for this demo (opt-in: set VELOCITY_RUN_E2E=1)."
    usesService(e2eServerLock) // serialize across demos — they share a service port
    workingDir = e2eDir.asFile
    // `npm ci` pins deps to the committed lockfile; the harness boots this demo via the Gradle
    // `run` task and drives the record/query HTTP contract. The exact runner is defined when the
    // demo's e2e/ suite lands.
    commandLine("sh", "-c", "npm ci --no-audit --no-fund && npm test")
}

// Wire into `check` only when opted in, so a default `check` (local or the CI `build` job) neither
// depends on nor runs the suite. `./gradlew :examples:<demo>:e2eTest` still runs it on demand.
if (runE2e) {
    tasks.named("check") {
        dependsOn(e2eTest)
    }
}
