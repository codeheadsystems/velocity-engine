plugins {
    java
    jacoco
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    "testImplementation"(platform("org.junit:junit-bom:${libs.versions.junit.jupiter.get()}"))
    "testImplementation"(libs.bundles.test)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

jacoco {
    // Pin the JaCoCo tooling version centrally so all modules report against the same engine.
    toolVersion = "0.8.12"
}

// Coverage excludes trivial/generated code so the 80% line / 70% branch gate (below) applies to
// LOGIC, not boilerplate. JaCoCo already filters record-generated accessors/equals/hashCode/toString,
// so a pure record/DTO contributes ~nothing to the denominator; these patterns additionally drop
// DTO/model packages and generated DI code from BOTH the report and the gate. A change that adds only
// DTOs/records therefore passes without artificial tests — the "no tests for trivial code" carve-out
// (see CONTRIBUTING.md → Definition of Done). Non-trivial logic MUST NOT be parked in an excluded
// package to dodge the gate.
val coverageExcludes = listOf(
    "**/dto/**",
    "**/model/**",
    "**/dagger/**",
    "**/Dagger*",
    "**/*_Factory.class",
    "**/*_MembersInjector.class",
    "**/*_Provide*Factory.class",
    "**/package-info.class",
    "**/module-info.class",
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
    )
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestReport"))
}

// Coverage gate for published library modules: LINE ≥80%, BRANCH ≥70%. Keyed off `java-library`
// (applied by velocity.library-conventions) so it covers every library module but NOT the example
// apps, which apply test-conventions only for the report. A module may layer on a STRICTER rule in
// its own build.gradle.kts — Gradle enforces all rules, so an override need only state the limits it
// raises, never restate this floor. This gate is what makes "tests required, ≥80% coverage" a
// REQUIREMENT and not a suggestion; the exclusions above keep it honest about trivial code. See
// CONTRIBUTING.md → Definition of Done and requirements NFR-14.
plugins.withId("java-library") {
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        classDirectories.setFrom(
            files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
        )
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}
