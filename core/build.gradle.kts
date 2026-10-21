plugins {
    `java-library`
    // Synthetic saves (FakeSave) shared with the app module's tests.
    `java-test-fixtures`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // SQLite driver used to read players.db and vehicles.db.
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    // Tests against real saves only run when ZCR_REAL_SAVES points at one or more
    // save folders (separated by the platform path separator). They are skipped otherwise.
    inputs.property("realSaves", System.getenv("ZCR_REAL_SAVES") ?: "")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
