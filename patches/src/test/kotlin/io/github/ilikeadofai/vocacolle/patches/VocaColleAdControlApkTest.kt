package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.patch.loadPatchesFromJar
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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

class VocaColleAdControlApkTest {
    @Test
    fun `applies ad controls and preserves registration in the real VocaColle APK`() {
        val outputPath = System.getProperty("vocacolle.matrix.output")
        assumeTrue(outputPath != null, "vocacolle.matrix.output is required")
        val sourceApk = System.getProperty("vocacolle.apk")
            ?.let(::File)
            ?: error("-Dvocacolle.apk is required")
        val outputRoot = File(outputPath!!).apply { mkdirs() }
        val work = File(outputRoot, "work-ad-control").apply {
            deleteRecursively()
            mkdirs()
        }
        val outputApk = File(outputRoot, "ad-control-only-unsigned.apk")
        sourceApk.copyTo(outputApk, overwrite = true)
        val selectedAdControlPatch = System.getProperty("vocacolle.mpp")
            ?.let(::File)
            ?.let { bundle ->
                loadPatchesFromJar(setOf(bundle)).single {
                    it.name == "VocaColle ad control"
                }
            }
            ?: vocacolleAdControlPatch

        val config = PatcherConfig(
            sourceApk,
            work,
            null,
            null,
            true,
            emptySet(),
            BytecodeMode.FULL,
            NoOpDexVerifier
        )
        Patcher(config).use { patcher ->
            patcher += setOf(selectedAdControlPatch)
            val failures = runBlocking { patcher().toList() }.filter { it.exception != null }
            assertTrue(
                failures.isEmpty(),
                failures.joinToString("\n") { "${it.patch.name}: ${it.exception?.stackTraceToString()}" }
            )
            patcher.get().applyTo(outputApk)
        }
        work.deleteRecursively()

        val currentVideoMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull {
                        it.type == "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;"
                    }?.methods?.firstOrNull {
                        it.name == "K4" &&
                            it.parameterTypes.map(CharSequence::toString) == listOf(
                                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                                "LEl/K;",
                                "Ljp/nicovideo/nicobox/ui/player/o\$j;"
                            )
                    }
                }
                .firstOrNull()
        }
        val currentVideoInstructions =
            assertNotNull(currentVideoMethod).implementation!!.instructions.toList()
        val mediaChangeIndex = currentVideoInstructions.indexOfFirst {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                reference.definingClass == "Ljava/util/Objects;" && reference.name == "equals"
            } == true
        }
        val progressResetIndex = currentVideoInstructions.indexOfFirst {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                reference.definingClass == "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;" &&
                    reference.name == "T5" && reference.parameterTypes.map(CharSequence::toString) == listOf("J")
            } == true
        }
        val titleUpdateIndex = currentVideoInstructions.indexOfFirst {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                reference.definingClass == "Ljp/nicovideo/nicobox/ui/player/o;" &&
                    reference.name == "h2"
            } == true
        }
        assertTrue(mediaChangeIndex >= 8)
        assertTrue(progressResetIndex in (mediaChangeIndex + 1) until titleUpdateIndex)
        val progressReset = currentVideoInstructions[progressResetIndex] as FiveRegisterInstruction
        assertEquals(9, progressReset.registerC)
        assertEquals(7, progressReset.registerD)
        assertEquals(8, progressReset.registerE)

        val method = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Llg/c;" }
                        ?.methods
                        ?.firstOrNull {
                            it.name == "n" &&
                                it.returnType == "Ljava/lang/Object;" &&
                                it.parameterTypes.map(CharSequence::toString) ==
                                listOf("Landroid/content/Context;", "Lsl/e;")
                        }
                }
                .firstOrNull()
        }
        val appOpenMethod = assertNotNull(method)
        val references = appOpenMethod.implementation!!.instructions
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { it.reference as? MethodReference }

        assertEquals(1, references.count {
            it.definingClass.endsWith("/ads/AdControl;") &&
                it.name == "appOpenAdOverride" &&
                it.returnType == "Ljava/lang/Boolean;"
        })
        assertEquals(
            listOf("invoke-static", "move-result-object", "if-eqz", "return-object"),
            appOpenMethod.implementation!!.instructions.take(4).map { it.opcode.name }
        )

        val audioMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Lwh/b;" }
                        ?.methods
                        ?.firstOrNull {
                            it.name == "e" &&
                                it.returnType == "Ljava/lang/Object;" &&
                                it.parameterTypes.map(CharSequence::toString) ==
                                listOf("Z", "Z", "Lsl/e;")
                        }
                }
                .firstOrNull()
        }
        val audioInstructions = assertNotNull(audioMethod).implementation!!.instructions.toList()
        val audioReferences = audioInstructions
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { it.reference as? MethodReference }
        assertEquals(2, audioReferences.count {
            it.definingClass.endsWith("/ads/AdControl;") &&
                it.name == "shouldBlockPlayerAds" &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Z"
        })
        assertEquals(0, audioReferences.count {
            it.definingClass.endsWith("/ads/AdControl;") &&
                it.name == "shouldBlockPlayerAds" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;") &&
                it.returnType == "Z"
        })
        val applicationContextIndex = audioInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "Landroid/content/Context;" &&
                    it.name == "getApplicationContext" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "Landroid/content/Context;"
            } == true
        }
        assertTrue(applicationContextIndex >= 0)
        val audioGuardIndices = audioInstructions.mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            if (
                reference?.definingClass?.endsWith("/ads/AdControl;") == true &&
                reference.name == "shouldBlockPlayerAds" &&
                reference.parameterTypes.isEmpty()
            ) index else null
        }
        assertEquals(2, audioGuardIndices.size)
        assertTrue(audioGuardIndices.all { it > applicationContextIndex + 2 })
        val initializationReferences = audioInstructions.mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            if (
                reference?.let {
                    (it.definingClass == "Lch/d;" && it.name == "b") ||
                        (it.definingClass == "Lmg/f;" && it.name == "d") ||
                        (it.definingClass == "Lmg/e;" && it.name == "m")
                } == true
            ) index else null
        }
        assertTrue(initializationReferences.isNotEmpty())
        assertTrue(initializationReferences.max() < audioGuardIndices.min())
        audioGuardIndices.forEach { audioGuardIndex ->
            assertEquals(
                listOf(
                    "invoke-static", "move-result", "if-eqz", "sget-object",
                    "return-object", "nop", "return-object"
                ),
                audioInstructions.drop(audioGuardIndex).take(7).map { it.opcode.name }
            )
            val audioGuardInvoke = audioInstructions[audioGuardIndex] as FiveRegisterInstruction
            assertEquals(0, audioGuardInvoke.registerCount)
            assertEquals(1, (audioInstructions[audioGuardIndex + 1] as OneRegisterInstruction).registerA)
            assertEquals(1, (audioInstructions[audioGuardIndex + 2] as OneRegisterInstruction).registerA)
            assertEquals(1, (audioInstructions[audioGuardIndex + 3] as OneRegisterInstruction).registerA)
            val noAdSentinel =
                (audioInstructions[audioGuardIndex + 3] as ReferenceInstruction).reference as FieldReference
            assertEquals("Lcf/c\$c;", noAdSentinel.definingClass)
            assertEquals("a", noAdSentinel.name)
            assertEquals("Lcf/c\$c;", noAdSentinel.type)
            assertTrue(
                (audioInstructions[audioGuardIndex + 6] as OneRegisterInstruction).registerA in setOf(0, 7)
            )
        }

        val audioEventMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Ljp/nicovideo/nicobox/service/player/g\$a;" }
                        ?.methods
                        ?.firstOrNull {
                            it.name == "b" &&
                                it.returnType == "V" &&
                                it.parameterTypes.map(CharSequence::toString) == listOf("Lcf/a;")
                        }
                }
                .firstOrNull()
        }
        val audioEventInstructions = assertNotNull(audioEventMethod).implementation!!.instructions.toList()
        assertEquals(
            listOf("invoke-static", "move-result", "if-eqz", "instance-of", "if-eqz", "sget-object", "move-object", "nop"),
            audioEventInstructions.take(8).map { it.opcode.name }
        )
        val eventPolicy = audioEventInstructions[0] as FiveRegisterInstruction
        assertEquals(0, eventPolicy.registerCount)
        assertEquals(0, (audioEventInstructions[1] as OneRegisterInstruction).registerA)
        val completedAudioState =
            (audioEventInstructions[5] as ReferenceInstruction).reference as FieldReference
        assertEquals("Lcf/a\$a;", completedAudioState.definingClass)
        assertEquals("a", completedAudioState.name)
        assertEquals("const-string", audioEventInstructions[8].opcode.name)

        val audioStateMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Ljp/nicovideo/nicobox/service/player/d\$k;" }
                        ?.methods
                        ?.firstOrNull {
                            it.name == "invokeSuspend" &&
                                it.returnType == "Ljava/lang/Object;" &&
                                it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;")
                        }
                }
                .firstOrNull()
        }
        val audioStateInstructions = assertNotNull(audioStateMethod).implementation!!.instructions.toList()
        assertTrue(audioStateInstructions.withIndex().any { (index, instruction) ->
            ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                it.definingClass == "Lcf/a\$c;" && it.name == "a"
            } == true &&
                ((audioStateInstructions.getOrNull(index + 1) as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                    it.definingClass == "Ljp/nicovideo/nicobox/service/player/d;" &&
                        it.name == "v" && it.returnType == "V"
                } == true
        })

        val networkAudioAdFallbackMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull {
                        it.type == "Ljp/nicovideo/nicobox/service/player/d\$k\$a;"
                    }?.methods?.firstOrNull {
                        it.name == "invokeSuspend" && it.returnType == "Ljava/lang/Object;" &&
                            it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;")
                    }
                }
                .firstOrNull()
        }
        val networkFallbackInstructions =
            assertNotNull(networkAudioAdFallbackMethod).implementation!!.instructions.toList()
        assertEquals(
            listOf("invoke-static", "move-result", "if-eqz", "sget-object", "return-object", "nop"),
            networkFallbackInstructions.take(6).map { it.opcode.name }
        )
        val networkFallbackGuard =
            (networkFallbackInstructions[0] as ReferenceInstruction).reference as MethodReference
        assertTrue(networkFallbackGuard.definingClass.endsWith("/ads/AdControl;"))
        assertEquals("shouldBlockPlayerAds", networkFallbackGuard.name)
        assertEquals("Z", networkFallbackGuard.returnType)
        assertEquals(emptyList(), networkFallbackGuard.parameterTypes.map(CharSequence::toString))
        assertEquals(0, (networkFallbackInstructions[1] as OneRegisterInstruction).registerA)
        val skippedFallbackResult =
            (networkFallbackInstructions[3] as ReferenceInstruction).reference as FieldReference
        assertEquals("Lnl/L;", skippedFallbackResult.definingClass)
        assertEquals("a", skippedFallbackResult.name)
        assertEquals("Lnl/L;", skippedFallbackResult.type)
        assertEquals(0, (networkFallbackInstructions[3] as OneRegisterInstruction).registerA)
        assertEquals(0, (networkFallbackInstructions[4] as OneRegisterInstruction).registerA)

        val originalFallbackReferences = networkFallbackInstructions.drop(6)
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { it.reference as? MethodReference }
        assertTrue(originalFallbackReferences.any {
            it.definingClass == "Lwh/f;" && it.name == "r" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/content/Context;", "Lce/c;", "Lsl/e;")
        })

        val playerConnectionSetupMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Ljp/nicovideo/nicobox/ui/player/o;" }
                        ?.methods?.firstOrNull {
                            it.name == "X" && it.returnType == "V" &&
                                it.parameterTypes.map(CharSequence::toString) ==
                                listOf("Landroid/content/Context;")
                        }
                }
                .firstOrNull()
        }
        val playerConnectionInstructions =
            assertNotNull(playerConnectionSetupMethod).implementation!!.instructions.toList()
        val listenerRegistrationIndex = playerConnectionInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "Ljp/nicovideo/nicobox/ui/player/e;" &&
                    it.name == "j" &&
                    it.parameterTypes.map(CharSequence::toString) ==
                    listOf("Ljp/nicovideo/nicobox/ui/player/e\$c;") &&
                    it.returnType == "V"
            } == true
        }
        assertTrue(listenerRegistrationIndex >= 0)
        assertEquals(
            listOf(
                "invoke-static", "move-result", "if-eqz", "sget-object", "invoke-interface", "nop"
            ),
            playerConnectionInstructions.drop(listenerRegistrationIndex + 1).take(6)
                .map { it.opcode.name }
        )
        assertEquals(
            2,
            (playerConnectionInstructions[listenerRegistrationIndex + 2] as OneRegisterInstruction).registerA,
            "The policy result must use dead v2, not the live connection/listener registers"
        )
        val completedStartupState =
            (playerConnectionInstructions[listenerRegistrationIndex + 4] as ReferenceInstruction)
                .reference as FieldReference
        assertEquals("Lcf/a\$a;", completedStartupState.definingClass)
        assertEquals("a", completedStartupState.name)
        val startupEventDispatch =
            playerConnectionInstructions[listenerRegistrationIndex + 5] as FiveRegisterInstruction
        assertEquals(2, startupEventDispatch.registerCount)
        assertEquals(1, startupEventDispatch.registerC)
        assertEquals(2, startupEventDispatch.registerD)
        val originalConnectionStore = playerConnectionInstructions[listenerRegistrationIndex + 7]
        assertEquals("iput-object", originalConnectionStore.opcode.name)

        val displayAdMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "LBj/b;" }
                        ?.methods
                        ?.firstOrNull {
                            it.name == "s" && it.returnType == "V" && it.parameterTypes.isEmpty()
                        }
                }
                .firstOrNull()
        }
        val displayAdInstructions = assertNotNull(displayAdMethod).implementation!!.instructions.toList()
        val displayAdGuardIndex = displayAdInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass.endsWith("/ads/AdControl;") &&
                    it.name == "shouldBlockDisplayAds" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;") &&
                    it.returnType == "Z"
            } == true
        }
        val displayAdDestroyIndex = displayAdInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "LBj/b;" &&
                    it.name == "q" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "V"
            } == true
        }
        val displayAdContextIndex = displayAdInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "LNh/f;" &&
                    it.name == "a" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "Landroid/content/Context;"
            } == true
        }
        assertTrue(displayAdDestroyIndex >= 0)
        assertTrue(displayAdDestroyIndex < displayAdContextIndex)
        assertEquals(displayAdContextIndex + 2, displayAdGuardIndex)
        assertEquals(
            listOf(
                "invoke-static", "move-result", "if-eqz", "invoke-static", "move-result-object",
                "sget-object", "invoke-virtual", "return-void", "nop", "if-nez"
            ),
            displayAdInstructions.drop(displayAdGuardIndex).take(10).map { it.opcode.name }
        )
        val displayAdGuardInvoke = displayAdInstructions[displayAdGuardIndex] as FiveRegisterInstruction
        assertEquals(1, displayAdGuardInvoke.registerCount)
        assertEquals(3, displayAdGuardInvoke.registerC)
        assertEquals(
            4,
            (displayAdInstructions[displayAdGuardIndex + 1] as OneRegisterInstruction).registerA,
            "Display-ad policy result must not overwrite the live v0 logger"
        )
        val displayStateAccessor =
            (displayAdInstructions[displayAdGuardIndex + 3] as ReferenceInstruction).reference as MethodReference
        assertEquals("LBj/b;", displayStateAccessor.definingClass)
        assertEquals("j", displayStateAccessor.name)
        assertEquals(
            4,
            (displayAdInstructions[displayAdGuardIndex + 4] as OneRegisterInstruction).registerA
        )
        val displayNoneState =
            (displayAdInstructions[displayAdGuardIndex + 5] as ReferenceInstruction).reference as FieldReference
        assertEquals("LBj/a;", displayNoneState.definingClass)
        assertEquals("b", displayNoneState.name)
        assertEquals(
            5,
            (displayAdInstructions[displayAdGuardIndex + 5] as OneRegisterInstruction).registerA
        )
        val displayStatePublisher =
            (displayAdInstructions[displayAdGuardIndex + 6] as ReferenceInstruction).reference as MethodReference
        assertEquals("Landroidx/lifecycle/E;", displayStatePublisher.definingClass)
        assertEquals("p", displayStatePublisher.name)
        val displayStatePublishInstruction =
            displayAdInstructions[displayAdGuardIndex + 6] as FiveRegisterInstruction
        assertEquals(4, displayStatePublishInstruction.registerC)
        assertEquals(5, displayStatePublishInstruction.registerD)

        val premiumHookedMethods = mutableSetOf<String>()
        var registrationLauncherAdControlReferences = -1
        var premiumRegistrationBinderAdControlReferences = -1
        var premiumRegistrationNoticeClasses = 0
        var premiumRegistrationNoticeAdControlReferences = 0
        ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .forEach { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.forEach { dexClass ->
                        dexClass.methods.forEach { method ->
                            val instructions = method.implementation?.instructions?.toList().orEmpty()
                            val methodReferences = instructions
                                ?.filterIsInstance<ReferenceInstruction>()
                                ?.mapNotNull { it.reference as? MethodReference }
                                ?.toList()
                                .orEmpty()
                            val premiumHookIndex = instructions.indexOfFirst { instruction ->
                                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                                reference?.definingClass?.endsWith("/ads/AdControl;") == true &&
                                    reference.name == "shouldHidePremiumPromotions"
                            }
                            if (!dexClass.type.endsWith("/ads/AdControl;") && premiumHookIndex >= 0) {
                                val hookedMethod = "${dexClass.type}->${method.name}(" +
                                    method.parameterTypes.joinToString("") + ")${method.returnType}"
                                premiumHookedMethods += hookedMethod
                                val isHighQualitySnackbar = hookedMethod ==
                                    "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;->" +
                                    "y4(Ljp/nicovideo/nicobox/ui/player/PlayerFragment;Lwh/l;)Lnl/L;"
                                val guardedOpcodes = instructions.drop(premiumHookIndex + 1)
                                    .take(if (method.returnType == "V") 4 else 5)
                                    .map { it.opcode.name }
                                assertEquals(
                                    if (isHighQualitySnackbar) {
                                        listOf("move-result", "if-eqz", "sget-object", "return-object", "const")
                                    } else if (method.returnType == "V") {
                                        listOf("move-result", "if-eqz", "return-void", "sget-object")
                                    } else {
                                        listOf(
                                            "move-result", "if-eqz", "sget-object", "return-object", "sget-object"
                                        )
                                    },
                                    guardedOpcodes,
                                    "Unexpected Premium guard shape in ${dexClass.type}->${method.name}"
                                )
                                if (isHighQualitySnackbar) {
                                    assertEquals(
                                        6,
                                        (instructions[premiumHookIndex + 1] as OneRegisterInstruction).registerA
                                    )
                                    assertEquals(
                                        6,
                                        (instructions[premiumHookIndex + 2] as OneRegisterInstruction).registerA
                                    )
                                    val unitField =
                                        (instructions[premiumHookIndex + 3]
                                            as ReferenceInstruction).reference as FieldReference
                                    assertEquals("Lnl/L;", unitField.definingClass)
                                    assertEquals("a", unitField.name)
                                    assertEquals("Lnl/L;", unitField.type)
                                    assertEquals(
                                        6,
                                        (instructions[premiumHookIndex + 3] as OneRegisterInstruction).registerA
                                    )
                                    assertEquals(
                                        6,
                                        (instructions[premiumHookIndex + 4] as OneRegisterInstruction).registerA
                                    )
                                    assertEquals(
                                        0x7f130553,
                                        (instructions[premiumHookIndex + 5]
                                            as NarrowLiteralInstruction).narrowLiteral
                                    )
                                    assertEquals(
                                        4,
                                        (instructions[premiumHookIndex + 5]
                                            as OneRegisterInstruction).registerA
                                    )
                                    return@forEach
                                }
                                val restoredFieldOffset = if (method.returnType == "V") 4 else 5
                                val restoredField =
                                    (instructions[premiumHookIndex + restoredFieldOffset]
                                        as ReferenceInstruction).reference as FieldReference
                                if (dexClass.type ==
                                    "Ljp/nicovideo/nicobox/ui/home/HomeFragment;"
                                ) {
                                    assertEquals(
                                        "Ljp/nicovideo/nicobox/ui/premiummerit/" +
                                            "PremiumMeritLeadInfoBottomSheetDialog;",
                                        restoredField.definingClass
                                    )
                                    assertEquals("i1", restoredField.name)
                                    assertEquals(
                                        "Ljp/nicovideo/nicobox/ui/premiummerit/" +
                                            "PremiumMeritLeadInfoBottomSheetDialog\$a;",
                                        restoredField.type
                                    )
                                } else {
                                    assertEquals(
                                        "Ljp/nicovideo/nicobox/ui/premiummerit/" +
                                            "PremiumMeritLeadDialog;",
                                        restoredField.definingClass
                                    )
                                    assertEquals("h1", restoredField.name)
                                    assertEquals(
                                        "Ljp/nicovideo/nicobox/ui/premiummerit/" +
                                            "PremiumMeritLeadDialog\$a;",
                                        restoredField.type
                                    )
                                }
                            }
                            if (
                                dexClass.type ==
                                "Ljp/nicovideo/nicobox/ui/premium/PremiumRegistrationActivity\$a;" &&
                                method.name == "a" &&
                                method.parameterTypes.map(CharSequence::toString) ==
                                listOf("Landroid/content/Context;", "Ljava/lang/String;")
                            ) {
                                registrationLauncherAdControlReferences = methodReferences.count {
                                    it.definingClass.endsWith("/ads/AdControl;")
                                }
                            }
                            if (
                                dexClass.type ==
                                "Ljp/nicovideo/nicobox/ui/premium/PremiumRegistrationActivity;" &&
                                method.name == "Q" &&
                                method.parameterTypes.map(CharSequence::toString) == listOf("LAk/t;") &&
                                method.returnType == "V"
                            ) {
                                premiumRegistrationBinderAdControlReferences = methodReferences.count {
                                    it.definingClass.endsWith("/ads/AdControl;")
                                }
                            }
                            if (dexClass.type.contains("PremiumRegistrationNoticeView")) {
                                premiumRegistrationNoticeAdControlReferences += methodReferences.count {
                                    it.definingClass.endsWith("/ads/AdControl;")
                                }
                            }
                        }
                        if (dexClass.type.contains("PremiumRegistrationNoticeView")) {
                            premiumRegistrationNoticeClasses += 1
                        }
                    }
                }
        }
        assertEquals(
            setOf(
                "LZi/r;->v(Landroidx/fragment/app/u;)V",
                "Lbj/t;->x(Landroidx/fragment/app/u;)V",
                "Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;->" +
                    "G2(Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;Lq0/u2;Lq0/u2;)Lnl/L;",
                "Ljp/nicovideo/nicobox/ui/publicmylist/PublicMylistFragment;->x3()V",
                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment\$k;->b(ZZ)V",
                "Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;->" +
                    "q2(Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;Lq0/u2;Z)Lnl/L;",
                "Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;->" +
                    "o2(Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;Z)Lnl/L;",
                "Ljp/nicovideo/nicobox/ui/home/HomeFragment;->" +
                    "N3(Ljp/nicovideo/nicobox/ui/home/HomeFragment;LFf/a;)Lnl/L;",
                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;->" +
                    "y4(Ljp/nicovideo/nicobox/ui/player/PlayerFragment;Lwh/l;)Lnl/L;"
            ),
            premiumHookedMethods
        )
        assertEquals(0, registrationLauncherAdControlReferences)
        assertEquals(0, premiumRegistrationBinderAdControlReferences)
        assertTrue(premiumRegistrationNoticeClasses > 0)
        assertEquals(0, premiumRegistrationNoticeAdControlReferences)

        val applicationOnCreateReferences = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull {
                        it.type == "Ljp/nicovideo/nicobox/ui/NicoboxApplication;"
                    }?.methods?.firstOrNull {
                        it.name == "onCreate" && it.parameterTypes.isEmpty() && it.returnType == "V"
                    }
                }
                .firstOrNull()
                ?.implementation
                ?.instructions
                ?.filterIsInstance<ReferenceInstruction>()
                ?.mapNotNull { it.reference as? MethodReference }
                ?.toList()
        }
        assertEquals(1, assertNotNull(applicationOnCreateReferences).count {
            it.definingClass.endsWith("/ads/AdControl;") && it.name == "initialize"
        })
        assertEquals(1, applicationOnCreateReferences.count {
            it.definingClass.endsWith("/settings/MorpheSettingsLauncher;") && it.name == "initialize"
        })

        val settingsRowMethod = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes(?:\\d+)?\\.dex")) }
                .mapNotNull { entry ->
                    val dex = zip.getInputStream(entry).use { input ->
                        DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(input))
                    }
                    dex.classes.firstOrNull { it.type == "Llj/j0;" }
                        ?.methods
                        ?.firstOrNull { it.name == "c" && it.returnType == "V" }
                }
                .firstOrNull()
        }
        assertTrue(assertNotNull(settingsRowMethod).implementation!!.instructions.any { instruction ->
            instruction.opcode == Opcode.CONST_16 &&
                (instruction as OneRegisterInstruction).registerA == 19 &&
                (instruction as NarrowLiteralInstruction).narrowLiteral == 0
        })
        assertTrue(outputApk.length() > 10_000_000L)
    }
}
