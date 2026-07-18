pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Maven Central publishing via Sonatype Central Portal. The aggregation plugin is auto-applied
    // to subprojects with `maven-publish` — `./gradlew publishAggregationToCentralPortal` uploads
    // every signed publication to the Central Portal in a single bundle (NFR-16).
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

rootProject.name = "velocity-engine"

includeBuild("build-logic")

// Central Portal credentials. Set CENTRAL_PORTAL_USERNAME / CENTRAL_PORTAL_PASSWORD (a user token)
// in CI; locally these resolve from `~/.gradle/gradle.properties` (centralPortalUsername /
// centralPortalPassword) if present, so a fresh clone doesn't crash without env vars.
nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_PORTAL_USERNAME")
            ?: providers.gradleProperty("centralPortalUsername").orNull
        password = System.getenv("CENTRAL_PORTAL_PASSWORD")
            ?: providers.gradleProperty("centralPortalPassword").orNull
    }
}

// Resolve project version from an exact Git tag (vX.Y.Z[-suffix]) so a tagged build produces the
// release version without editing gradle.properties. Local builds and untagged HEADs fall through
// to the SNAPSHOT version pinned in gradle.properties.
gradle.beforeProject {
    val gitVersion = providers.exec {
        commandLine("git", "describe", "--tags", "--exact-match", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

    if (gitVersion.startsWith("v")) {
        val match = Regex("^v(\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?)$").matchEntire(gitVersion)
        if (match != null) {
            version = match.groupValues[1]
            logger.lifecycle("Using version from Git tag: $version")
        } else {
            logger.warn("Git tag '$gitVersion' does not match semantic versioning format (vX.Y.Z)")
        }
    }
}

// Modules are added phase by phase (see docs/requirements.md §6.2 module layout).
//
// Phase 1 — the contract + engine core, frozen before any backend ships.
include("velocity-spi")      // the contract module: capability mix-ins, BackendCapabilities, SPI DTOs
include("velocity-core")     // engine, fan-out resolver, window/aggregation model, YAML I/O
include("velocity-api")      // OpenAPI 3.1 document + shared API DTOs (source of truth)
include("velocity-testkit")  // in-memory velocity-spi impl, conformance TCK scenarios, fixtures

// Phase 2 — v1 reference backends (tumbling + sliding), the service tier, and the generated client.
// include("velocity-backend-jdbi")   // Postgres/JDBI backend — v1 tumbling reference
// include("velocity-backend-redis")  // Redis/Lettuce backend — v1 sliding / hot-path reference
// include("velocity-dropwizard")     // Dropwizard bundle, Jersey resources, Dagger wiring, API-key auth
// include("velocity-client-java")    // Java client generated from OpenAPI via openapi-generator
// include("examples:dropwizard-demo")// runnable reference service (not published)

// Phase 3 — volume backend.
// include("velocity-backend-dynamodb")  // DynamoDB backend (single-table, atomic ADD)

// Phase 4 — cost backend.
// include("velocity-backend-s3")        // S3 backend (tumbling, approximate)
