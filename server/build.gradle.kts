plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "bd.sammalani"
version = "0.1.0"
description = "Alumni platform and Grand Reunion 2027 backend for Sammalani Secondary School"

java {
    toolchain {
        // Pinned, not inherited: the build must not silently compile to whatever
        // JDK happens to be on the machine that runs it.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "3.0.3"
val bouncyCastleVersion = "1.82"

dependencies {
    // web + validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 moved each technology's auto-configuration into its own module, so
    // flyway-core on its own would sit on the classpath and never run.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // cache + redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    // Lettuce connection pooling is opt-in and needs this on the classpath.
    implementation("org.apache.commons:commons-pool2")

    // security: password hashing plus JWT via Nimbus, so no second JSON stack
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // Argon2id lives behind BouncyCastle; Spring's Argon2PasswordEncoder needs it present.
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")

    // object storage (MinIO — S3-compatible, self-hosted)
    // Exclude Jackson transitive deps to avoid version collisions with Spring Boot's own Jackson.
    implementation("io.minio:minio:8.5.17") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.datatype")
    }

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // ops
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<JavaCompile>().configureEach {
    // Keep parameter names in the bytecode: Spring binds @RequestParam and
    // constructor arguments by name, and -parameters is what makes that work
    // without repeating every name in an annotation.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
