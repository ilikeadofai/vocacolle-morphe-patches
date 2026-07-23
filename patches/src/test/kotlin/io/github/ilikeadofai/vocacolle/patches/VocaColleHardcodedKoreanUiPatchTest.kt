package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.patch.ApkFileType
import kotlin.test.Test
import kotlin.test.assertEquals
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
}