plugins {
    // Lets Gradle fetch the JDK 25 toolchain itself, so the build does not depend
    // on which JDK happens to be installed on the machine running it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "alumni-service"
