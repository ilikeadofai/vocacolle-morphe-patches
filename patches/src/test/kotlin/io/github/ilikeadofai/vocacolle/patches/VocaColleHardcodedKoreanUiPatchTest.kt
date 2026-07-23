package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.patch.ApkFileType
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VocaColleHardcodedKoreanUiPatchTest {
    @Test
    fun `declares a default-enabled hardcoded UI patch for VocaColle 7_40_0`() {
        val patch = vocacolleHardcodedKoreanUiPatch

        assertEquals("Korean hardcoded UI", patch.name)
        assertTrue(patch.default)

        val compatibility = assertNotNull(patch.compatibility).single()
        assertEquals("jp.nicovideo.nicobox", compatibility.packageName)
        assertEquals(ApkFileType.APK, compatibility.apkFileType)
        assertEquals(listOf("7.40.0"), compatibility.targets.map { it.version })
    }

    @Test
    fun `injects the runtime language translator after exact source literals`() {
        val patchSource = Path.of(
            "src/main/kotlin/io/github/ilikeadofai/vocacolle/patches/" +
                "VocaColleHardcodedKoreanUiPatch.kt",
        ).toFile().readText()

        assertContains(patchSource, "extendWith(\"extensions/extension.mpe\")")
        assertContains(patchSource, "UiLanguageTranslator;")
        assertContains(patchSource, "translateHardcoded(Ljava/lang/String;)Ljava/lang/String;")
        assertContains(patchSource, "addInstructions(")
        assertContains(patchSource, "move-result-object v\$register")
        assertFalse(patchSource.contains("const-string v\$register, \\\"\$replacement\\\""))
    }
}