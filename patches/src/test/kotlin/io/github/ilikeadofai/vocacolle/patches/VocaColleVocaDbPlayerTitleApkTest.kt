package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VocaColleVocaDbPlayerTitleApkTest {
    @Test
    fun `applies exact asynchronous player title hooks to the real APK`() {
        val outputPath = System.getProperty("vocacolle.matrix.output")
        assumeTrue(outputPath != null, "vocacolle.matrix.output is required")
        val sourceApk = System.getProperty("vocacolle.apk")
            ?.let(::File)
            ?: error("-Dvocacolle.apk is required")
        val outputRoot = File(outputPath!!).apply { mkdirs() }
        val work = File(outputRoot, "work-vocadb-player-title").apply {
            deleteRecursively()
            mkdirs()
        }
        val outputApk = File(outputRoot, "vocadb-player-title-unsigned.apk")
        sourceApk.copyTo(outputApk, overwrite = true)

        Patcher(
            PatcherConfig(
                sourceApk,
                work,
                null,
                null,
                true,
                emptySet(),
                BytecodeMode.FULL,
                NoOpDexVerifier
            )
        ).use { patcher ->
            patcher += setOf(vocacolleVocaDbPlayerTitlePatch, vocacolleAdControlPatch)
            val failures = runBlocking { patcher().toList() }.filter { it.exception != null }
            assertTrue(
                failures.isEmpty(),
                failures.joinToString("\n") { "${it.patch.name}: ${it.exception?.stackTraceToString()}" }
            )
            patcher.get().applyTo(outputApk)
        }
        work.deleteRecursively()

        val methods = readMethods(outputApk)
        val currentVideo = assertNotNull(methods.firstOrNull {
            it.definingClass == "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;" &&
                it.name == "K4" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(
                    "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                    "LEl/K;",
                    "Ljp/nicovideo/nicobox/ui/player/o\$j;"
                )
        })
        val currentInstructions = currentVideo.implementation!!.instructions.toList()
        val enrichmentIndex = currentInstructions.indexOfFirst {
            it.methodReference()?.let { reference ->
                reference.definingClass.endsWith("/metadata/PlayerTitleEnrichment;") &&
                    reference.name == "onBound"
            } == true
        }
        assertTrue(enrichmentIndex >= 3)
        assertEquals(
            listOf("invoke-virtual/range", "invoke-virtual", "move-result-object", "invoke-static/range"),
            currentInstructions.drop(enrichmentIndex - 3).take(4).map { it.opcode.name }
        )
        val enrichmentInvoke = currentInstructions[enrichmentIndex] as RegisterRangeInstruction
        assertEquals(0, enrichmentInvoke.startRegister)
        assertEquals(7, enrichmentInvoke.registerCount)
        assertEquals(
            listOf(
                "Landroid/view/View;",
                "Ljava/lang/Object;",
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "I",
                "Z"
            ),
            currentInstructions[enrichmentIndex].methodReference()!!.parameterTypes
                .map(CharSequence::toString)
        )

        val titleState = assertNotNull(methods.firstOrNull {
            it.definingClass == "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;" &&
                it.name == "J4" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(
                    "LEl/M;",
                    "LEl/O;",
                    "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                    "Ljp/nicovideo/nicobox/ui/player/o\$h;"
                )
        })
        val titleInstructions = titleState.implementation!!.instructions.toList()
        val bindingKeyIndex = titleInstructions.indexOfFirst {
            it.methodReference()?.let { reference ->
                reference.definingClass.endsWith("/metadata/PlayerTitleBridge;") &&
                    reference.name == "bindingKey"
            } == true
        }
        assertTrue(bindingKeyIndex >= 2)
        assertEquals(
            listOf("invoke-virtual", "move-result-object", "invoke-static", "move-result-object", "iget"),
            titleInstructions.drop(bindingKeyIndex - 2).take(5).map { it.opcode.name }
        )

        val application = assertNotNull(methods.firstOrNull {
            it.definingClass == "Ljp/nicovideo/nicobox/ui/NicoboxApplication;" &&
                it.name == "onCreate" && it.parameterTypes.isEmpty()
        })
        assertEquals(1, application.implementation!!.instructions.count {
            it.methodReference()?.let { reference ->
                reference.definingClass.endsWith("/metadata/MetadataControl;") &&
                    reference.name == "markHooksInstalled"
            } == true
        })
    }

    private fun readMethods(apk: File): List<Method> = ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
            .flatMap { entry ->
                val dex = zip.getInputStream(entry).use { input ->
                    DexBackedDexFile.fromInputStream(
                        Opcodes.getDefault(),
                        BufferedInputStream(input)
                    )
                }
                dex.classes.asSequence().flatMap { it.methods.asSequence() }
            }
            .toList()
    }

    private fun Any.methodReference(): MethodReference? =
        ((this as? ReferenceInstruction)?.reference as? MethodReference)
}
