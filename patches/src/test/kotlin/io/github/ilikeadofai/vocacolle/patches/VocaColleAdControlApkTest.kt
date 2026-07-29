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
        assertEquals(1, audioReferences.count {
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
        val audioGuardIndex = audioInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass.endsWith("/ads/AdControl;") &&
                    it.name == "shouldBlockPlayerAds" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;")
            } == true
        }
        assertEquals(applicationContextIndex + 2, audioGuardIndex)
        assertEquals(
            listOf("invoke-static", "move-result", "if-eqz", "sget-object", "return-object"),
            audioInstructions.drop(audioGuardIndex).take(5).map { it.opcode.name }
        )
        val audioGuardInvoke = audioInstructions[audioGuardIndex] as FiveRegisterInstruction
        assertEquals(1, audioGuardInvoke.registerCount)
        assertEquals(9, audioGuardInvoke.registerC)
        assertEquals(
            listOf(2, 2, 2),
            listOf(1, 3, 4).map {
                (audioInstructions[audioGuardIndex + it] as OneRegisterInstruction).registerA
            }
        )
        val noAdSentinel =
            (audioInstructions[audioGuardIndex + 3] as ReferenceInstruction).reference as FieldReference
        assertEquals("Lcf/c\$c;", noAdSentinel.definingClass)
        assertEquals("a", noAdSentinel.name)
        assertEquals("Lcf/c\$c;", noAdSentinel.type)
        assertEquals(
            listOf(Opcode.NOP, Opcode.IF_NEZ),
            audioInstructions.drop(audioGuardIndex + 5).take(2).map { it.opcode }
        )
        assertEquals(
            8,
            (audioInstructions[audioGuardIndex + 6] as OneRegisterInstruction).registerA
        )

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
                                premiumHookedMethods +=
                                    "${dexClass.type}->${method.name}(" +
                                        method.parameterTypes.joinToString("") + ")${method.returnType}"
                                val guardedOpcodes = instructions.drop(premiumHookIndex + 1)
                                    .take(if (method.returnType == "V") 4 else 5)
                                    .map { it.opcode.name }
                                assertEquals(
                                    if (method.returnType == "V") {
                                        listOf("move-result", "if-eqz", "return-void", "sget-object")
                                    } else {
                                        listOf(
                                            "move-result", "if-eqz", "sget-object", "return-object", "sget-object"
                                        )
                                    },
                                    guardedOpcodes,
                                    "Unexpected Premium guard shape in ${dexClass.type}->${method.name}"
                                )
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
                    "N3(Ljp/nicovideo/nicobox/ui/home/HomeFragment;LFf/a;)Lnl/L;"
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
