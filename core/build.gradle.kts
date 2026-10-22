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

// Development tools (not part of the application).
val tools: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// Rebuilds the bundled tile table from a game installation (only the derived numbers are written):
//   ./gradlew :core:generateTileTable -PpzMedia=<Project Zomboid>/media
tasks.register<JavaExec>("generateTileTable") {
    group = "development"
    description = "Builds src/main/resources/.../tile-table.bin from the game's .tiles files."
    classpath = tools.runtimeClasspath
    mainClass = "zomboidchunkreset.tools.TileTableGenerator"
    val media = providers.gradleProperty("pzMedia")
    doFirst {
        if (!media.isPresent) {
            throw GradleException("Set -PpzMedia=<path to the game's media folder>")
        }
    }
    args(media.getOrElse(""),
        layout.projectDirectory.file("src/main/resources/zomboidchunkreset/core/terrain/tile-table.bin").asFile.path)
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
