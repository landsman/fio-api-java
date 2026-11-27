plugins {
    `java-library`
}

description = "A client for Fio banka's API Bankovnictví: movements, and payment orders to sign"

dependencies {
    // Nullability annotations are part of the API, so Kotlin callers see String and String? correctly.
    api("org.jspecify:jspecify:1.0.1")
    // No Jackson type leaves the library, and databind and core of the two majors live in different packages,
    // so a caller on Jackson 2 keeps it. jackson-annotations is shared with Jackson 2; see README.md.
    // The BOM keeps databind and core on one version; core is named because JacksonException lives there.
    implementation(platform("tools.jackson:jackson-bom:3.3.0"))
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.core:jackson-core")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    // Pinned, so a JPMS caller's `requires` does not change with the jar's file name.
    manifest { attributes("Automatic-Module-Name" to "cz.insuit.fio") }
}

// The oldest Jackson a caller is likely to resolve this to: Spring Boot 4.0.0's. Running the same tests on it
// catches a call to an API newer than that, which would compile here and fail there with NoSuchMethodError.
val jacksonFloor =
    configurations.create("jacksonFloor") {
        isCanBeConsumed = false
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
        // Pinned on purpose, and not written as "group:name:version": Dependabot would take that for a dependency
        // and bump it, raising the floor this test exists to hold.
        val floor =
            mapOf(
                "tools.jackson.core:jackson-databind" to "3.0.4",
                "tools.jackson.core:jackson-core" to "3.0.4",
                "com.fasterxml.jackson.core:jackson-annotations" to "2.20",
            )
        resolutionStrategy.force(floor.map { (module, version) -> "$module:$version" })
    }

val jacksonFloorTest =
    tasks.register<Test>("jacksonFloorTest") {
        description = "Runs the tests on the oldest Jackson 3 the library supports."
        group = "verification"
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + jacksonFloor
    }

// Trivy reads Gradle only through gradle.lockfile. Only what ships is locked.
configurations.named("runtimeClasspath") {
    resolutionStrategy.activateDependencyLocking()
}

// Nothing else in the build resolves runtimeClasspath, so a stale lockfile would pass and Trivy would scan
// versions that do not ship. Resolving it here fails the build on any drift; `make lock` rewrites the file.
val verifyLocks =
    tasks.register("verifyLocks") {
        description = "Fails when gradle.lockfile no longer matches the runtime classpath."
        group = "verification"
        inputs.files(configurations.runtimeClasspath)
        doLast {}
    }

tasks.check {
    dependsOn(jacksonFloorTest, verifyLocks)
}
