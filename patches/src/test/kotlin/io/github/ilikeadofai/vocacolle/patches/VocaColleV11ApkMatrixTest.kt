package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.patch.Patch
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class VocaColleV11ApkMatrixTest {
    @Test
    fun `full dex rebuild matrix applies every v1_1 combination`() {
        val outputPath = System.getProperty("vocacolle.matrix.output")
        assumeTrue(
            outputPath != null,
            "vocacolle.matrix.output is required for APK matrix verification"
        )
        val outputRoot = File(outputPath!!)
        val sourceApk = System.getProperty("vocacolle.apk")
            ?.let(::File)
            ?: error("-Dvocacolle.apk is required with -Dvocacolle.matrix.output")
        require(sourceApk.isFile) { "Missing source APK: $sourceApk" }
        outputRoot.mkdirs()

        val settings = setOf<Patch<*>>(vocacolleMorpheSettingsPatch)
        val localization = setOf<Patch<*>>(
            vocacolleKoreanUiPatch,
            vocacolleEnglishUiPatch
        )
        val variants = linkedMapOf(
            "settings-only" to settings,
            "localization-only" to localization,
            "full-default" to settings + localization
        )

        variants.forEach { (name, patches) ->
            val temporaryFiles = File(outputRoot, "work-$name").apply {
                deleteRecursively()
                mkdirs()
            }
            val outputApk = File(outputRoot, "$name-unsigned.apk")
            sourceApk.copyTo(outputApk, overwrite = true)
            val config = PatcherConfig(
                sourceApk,
                temporaryFiles,
                null,
                null,
                true,
                emptySet(),
                BytecodeMode.FULL,
                NoOpDexVerifier
            )

            Patcher(config).use { patcher ->
                patcher += patches
                val results = runBlocking { patcher().toList() }
                val failures = results.filter { it.exception != null }
                assertTrue(
                    failures.isEmpty(),
                    failures.joinToString(separator = "\n") {
                        "${it.patch.name}: ${it.exception?.stackTraceToString()}"
                    }
                )
                patcher.get().applyTo(outputApk)
            }

            temporaryFiles.deleteRecursively()
            assertTrue(outputApk.isFile, "Matrix output was not created: $outputApk")
            assertTrue(outputApk.length() > 10_000_000L, "Matrix output is unexpectedly small: $outputApk")
            println("MATRIX_OK $name ${outputApk.absolutePath} ${outputApk.length()}")
        }
    }
}
