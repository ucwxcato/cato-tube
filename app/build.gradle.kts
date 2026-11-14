plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Kotlin serialization for library.json (version from gradle/libs.versions.toml).
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))

    // utils uses `implementation`, so app declares the JSON library it needs directly.
    implementation(libs.kotlinxSerialization)

    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "org.catosaur.app.AppKt"

    // Needed for the Windows title bar call (NativeTitleBar) when run through :app:run.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        // Lets the executable JAR call Windows DWM for the dark native title bar without a warning.
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { file ->
        if (file.isDirectory) file else zipTree(file)
    })
}

// Copies the fat JAR into the directory jpackage reads from.
val stageAppImage by tasks.registering(Sync::class) {
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("jpackage-input"))
}

// Builds build/installer/CatoTube/ with jpackage from the JDK 25 toolchain. External tools (yt-dlp,
// ffmpeg, ffprobe, deno) are not bundled. The runtime module list comes from
// `jdeps --print-module-deps app-all.jar`; rerun it when a new JDK module is imported.
val packageWindows by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the CatoTube app image with jpackage into build/installer/CatoTube."
    dependsOn(stageAppImage)

    val isWindows = System.getProperty("os.name").startsWith("Windows")
    val toolchainHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        .get().metadata.installationPath.asFile
    val jpackage = toolchainHome.resolve(if (isWindows) "bin/jpackage.exe" else "bin/jpackage")
    val inputDir = layout.buildDirectory.dir("jpackage-input").get().asFile
    val destDir = layout.buildDirectory.dir("installer").get().asFile
    val appImage = destDir.resolve("CatoTube")
    val icon = rootProject.file("assets/app-icon.ico")
    val iconArgs = if (icon.exists()) listOf("--icon", icon.absolutePath) else emptyList()

    inputs.dir(inputDir)
    inputs.files(listOf(icon)).optional()
    outputs.dir(appImage)

    doFirst {
        // jpackage refuses to overwrite an existing app image. Windows can hold files briefly (scanners,
        // a process still exiting), so retry before giving up.
        if (appImage.exists()) {
            repeat(5) {
                if (appImage.deleteRecursively()) return@doFirst
                Thread.sleep(1000)
            }
            throw GradleException("Could not remove $appImage. Close CatoTube and run the task again.")
        }
    }

    commandLine(
        *(
            listOf(
                jpackage.absolutePath,
                "--type", "app-image",
                "--name", "CatoTube",
                "--app-version", "2.0.0",
                "--vendor", "CatoTube",
                "--description", "Saves YouTube audio to a local music library and plays it offline",
                "--input", inputDir.absolutePath,
                "--main-jar", "app-all.jar",
                "--main-class", application.mainClass.get(),
                "--dest", destDir.absolutePath,
                "--java-options", "--enable-native-access=ALL-UNNAMED",
                "--add-modules", "java.base,java.desktop,java.instrument,java.prefs,jdk.unsupported",
                "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files",
            ) + iconArgs
            ).toTypedArray()
    )
}

