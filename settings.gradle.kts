rootProject.name = "fio-api-java"

// What ships: the client, and the Spring Boot starter around it.
include(
    ":modules:fio-api",
    ":modules:fio-api-spring-boot-starter",
)
