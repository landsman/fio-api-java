plugins {
    `java-library`
}

description = "Spring Boot auto-configuration for the Fio banka API client"

// Compiled against the oldest Spring Boot it supports; the application's own Spring Boot is what runs it.
// Dependabot leaves this BOM alone (.github/dependabot.yml).
val springBoot = dependencies.platform("org.springframework.boot:spring-boot-dependencies:3.5.16")

dependencies {
    api(project(":modules:fio-api"))
    // FioProperties carries its nullability too; declared, not borrowed from the client.
    api("org.jspecify:jspecify:1.0.1")
    compileOnly(springBoot)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework:spring-context")
    // Writes the metadata IntelliJ reads to complete fio.* in application.yml.
    annotationProcessor(springBoot)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(springBoot)
    testImplementation("org.springframework.boot:spring-boot")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-context")
    // ApplicationContextRunner hands the test an AssertJ type, so it has to be on the classpath.
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyAnalysis {
    issues {
        // Never imported, but AssertableApplicationContext extends its AssertProvider, so javac needs it.
        onUnusedDependencies { exclude("org.assertj:assertj-core") }
        onUsedTransitiveDependencies { exclude("org.springframework.boot:spring-boot-configuration-processor") }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.jar {
    manifest { attributes("Automatic-Module-Name" to "cz.insuit.fio.spring") }
}
