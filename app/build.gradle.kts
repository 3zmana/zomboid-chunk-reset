plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val javafxVersion = libs.versions.javafx.get()
val isWindows = System.getProperty("os.name").lowercase().contains("win")

// JavaFX ships one jar per platform (classifier). Pick the one of the machine running the build:
// Windows builds are made on Windows and Linux builds on Linux.
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

dependencies {
    implementation(project(":core"))
    for (module in listOf("base", "graphics", "controls")) {
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform") {
            isTransitive = false
        }
    }

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "zomboidchunkreset.app.Launcher"
    applicationName = "ZomboidChunkReset"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
}

// Version and repository address, read by Help > About (single source: gradle.properties).
tasks.processResources {
    val info = mapOf(
        "version" to project.version.toString(),
        "repository" to (findProperty("repositoryUrl") ?: "").toString(),
    )
    inputs.properties(info)
    filesMatching("app.properties") { expand(info) }
}

// ---------------------------------------------------------------------------------------------
// Packaging: a self-contained application folder (Java runtime included) made with jlink and
// jpackage from the JDK 21 toolchain, then a zip (Windows) or an AppImage (Linux).
//   gradlew :app:packageWindows   -> app/build/distributions/ZomboidChunkReset-<version>-windows-x64.zip
//   gradlew :app:packageLinux     -> app/build/distributions/ZomboidChunkReset-<version>-x86_64.AppImage
// packageLinux needs appimagetool: pass its path with -Pappimagetool=... (or the APPIMAGETOOL
// environment variable), otherwise "appimagetool" is looked up on the PATH.
// ---------------------------------------------------------------------------------------------

val appName = "ZomboidChunkReset"
val appVersion = project.version.toString()
val exeSuffix = if (isWindows) ".exe" else ""
val packagingDir = layout.buildDirectory.dir("jpackage")
val toolchainHome = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}.map { it.metadata.installationPath }

// The program runs on the class path; these modules are enough for it, JavaFX and the SQLite driver.
// jdk.localedata provides number formats of other languages; jdk.charsets the file-name encodings
// of every Windows locale.
val runtimeModules = listOf(
    "java.base", "java.desktop", "java.logging", "java.naming", "java.sql", "java.xml",
    "jdk.unsupported", "jdk.localedata", "jdk.charsets",
)

val jlinkRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a minimal Java runtime for the packaged application."
    val output = packagingDir.map { it.dir("runtime") }
    inputs.property("modules", runtimeModules)
    outputs.dir(output)
    doFirst {
        delete(output)
        executable = toolchainHome.get().file("bin/jlink$exeSuffix").asFile.absolutePath
        args(
            "--add-modules", runtimeModules.joinToString(","),
            "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-6",
            "--output", output.get().asFile.absolutePath,
        )
    }
}

val jpackageInput by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Collects the application jars for jpackage."
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packagingDir.map { it.dir("input") })
}

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the self-contained application folder with jpackage."
    dependsOn(jlinkRuntime, jpackageInput)
    val output = packagingDir.map { it.dir("image") }
    val icon = file(if (isWindows) "packaging/icon.ico" else "packaging/icon.png")
    inputs.dir(packagingDir.map { it.dir("input") })
    inputs.dir(packagingDir.map { it.dir("runtime") })
    inputs.file(icon)
    outputs.dir(output)
    doFirst {
        delete(output)
        executable = toolchainHome.get().file("bin/jpackage$exeSuffix").asFile.absolutePath
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        args(
            "--type", "app-image",
            "--name", appName,
            "--app-version", appVersion,
            "--vendor", "ZomboidChunkReset contributors",
            "--description", "Reset chunks of Project Zomboid saves while protecting chosen areas",
            "--input", packagingDir.get().dir("input").asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--runtime-image", packagingDir.get().dir("runtime").asFile.absolutePath,
            "--dest", output.get().asFile.absolutePath,
        )
        if (!isMac) {
            args("--icon", icon.absolutePath)
        }
    }
}

val packageWindows by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the Windows application folder (run on Windows)."
    dependsOn(jpackageImage)
    onlyIf("runs on Windows only") { isWindows }
    archiveFileName = "$appName-$appVersion-windows-x64.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(packagingDir.map { it.dir("image") })
    from(rootProject.file("LICENSE")) { into(appName) }
    from(rootProject.file("README.md")) { into(appName) }
    from(rootProject.file("THIRD-PARTY-NOTICES.md")) { into(appName) }
}

val linuxAppDir by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Lays out the AppImage folder (run on Linux)."
    dependsOn(jpackageImage)
    onlyIf("runs on Linux only") { !isWindows }
    into(layout.buildDirectory.dir("appimage/$appName.AppDir"))
    from(packagingDir.map { it.dir("image/$appName") }) { into("usr/lib/$appName") }
    from("packaging/linux") {
        include("AppRun")
        filePermissions { unix("rwxr-xr-x") }
    }
    from("packaging/linux") { include("*.desktop", "*.png") }
    from("packaging/linux/zomboidchunkreset.png") { rename { ".DirIcon" } }
    from(rootProject.file("LICENSE")) { into("usr/share/doc/zomboidchunkreset") }
    from(rootProject.file("THIRD-PARTY-NOTICES.md")) { into("usr/share/doc/zomboidchunkreset") }
}

val packageLinux by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the Linux AppImage (run on Linux, needs appimagetool)."
    dependsOn(linuxAppDir)
    onlyIf("runs on Linux only") { !isWindows }
    val appDir = layout.buildDirectory.dir("appimage/$appName.AppDir")
    val output = layout.buildDirectory.file("distributions/$appName-$appVersion-x86_64.AppImage")
    val tool = providers.gradleProperty("appimagetool").orElse(providers.environmentVariable("APPIMAGETOOL"))
        .orElse("appimagetool")
    outputs.file(output)
    doFirst {
        output.get().asFile.parentFile.mkdirs()
        // Run appimagetool without FUSE (not available on CI machines).
        environment("APPIMAGE_EXTRACT_AND_RUN", "1")
        environment("ARCH", "x86_64")
        commandLine(tool.get(), "--no-appstream", appDir.get().asFile.absolutePath, output.get().asFile.absolutePath)
    }
}
