group = "io.github.ilikeadofai.vocacolle"

patches {
    about {
        name = "ilikeadofai Patches"
        description = "Localization, settings, and opt-in controls for VocaColle"
        source = "https://github.com/ilikeadofai/vocacolle-morphe-patches"
        author = "ilikeadofai"
        contact = "https://github.com/ilikeadofai"
        website = "https://github.com/ilikeadofai/vocacolle-morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.test {
    useJUnitPlatform()
    listOf("vocacolle.apk", "vocacolle.matrix.output", "vocacolle.mpp").forEach { propertyName ->
        System.getProperty(propertyName)?.let { propertyValue ->
            systemProperty(propertyName, propertyValue)
        }
    }
    systemProperty(
        "vocacolle.translationCatalog",
        rootProject.file("translations/ui/ko.csv").absolutePath
    )
    systemProperty(
        "vocacolle.englishTranslationCatalog",
        rootProject.file("translations/ui/en.csv").absolutePath
    )
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn("buildAndroid")

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
        args(layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile.absolutePath)
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}