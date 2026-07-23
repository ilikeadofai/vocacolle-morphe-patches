package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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
            patcher += setOf(vocacolleMorpheSettingsPatch, vocacolleAdControlPatch)
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
        val audioReferences = assertNotNull(audioMethod).implementation!!.instructions
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { it.reference as? MethodReference }
        assertEquals(1, audioReferences.count {
            it.definingClass.endsWith("/ads/AdControl;") &&
                it.name == "shouldBlockPlayerAds" &&
                it.returnType == "Z"
        })
        assertEquals(
            listOf("invoke-static", "move-result", "if-eqz", "sget-object", "return-object"),
            audioMethod.implementation!!.instructions.take(5).map { it.opcode.name }
        )

        val premiumHookedMethods = mutableSetOf<Triple<String, String, String>>()
        var registrationLauncherAdControlReferences = -1
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
                                premiumHookedMethods += Triple(dexClass.type, method.name, method.returnType)
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
                Triple("LZi/r;", "v", "V"),
                Triple("Lbj/t;", "x", "V"),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;", "G2", "Lnl/L;"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/publicmylist/PublicMylistFragment;", "x3", "V"
                ),
                Triple("Ljp/nicovideo/nicobox/ui/player/PlayerFragment\$k;", "b", "V"),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;", "q2", "Lnl/L;"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;", "o2", "Lnl/L;"
                )
            ),
            premiumHookedMethods
        )
        assertEquals(0, registrationLauncherAdControlReferences)
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
