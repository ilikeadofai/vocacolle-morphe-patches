package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.patch.ApkFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VocaColleVocaDbPlayerTitlePatchTest {
    @Test
    fun `declares an opt-in exact VocaDB player title patch`() {
        val patch = vocacolleVocaDbPlayerTitlePatch

        assertEquals("VocaDB player titles", patch.name)
        assertFalse(patch.default)
        assertTrue(patch.dependencies.contains(vocacolleMorpheSettingsPatch))
        val compatibility = assertNotNull(patch.compatibility).single()
        assertEquals("jp.nicovideo.nicobox", compatibility.packageName)
        assertEquals(ApkFileType.APK, compatibility.apkFileType)
        assertEquals(listOf("7.40.0"), compatibility.targets.map { it.version })
    }

    @Test
    fun `fingerprints exact current-video and title-state presentation boundaries`() {
        assertEquals("Ljp/nicovideo/nicobox/ui/player/PlayerFragment;", currentVideoInfoFingerprint.definingClass)
        assertEquals("K4", currentVideoInfoFingerprint.name)
        assertEquals(
            listOf(
                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                "LEl/K;",
                "Ljp/nicovideo/nicobox/ui/player/o\$j;"
            ),
            currentVideoInfoFingerprint.parameters
        )
        assertNotNull(currentVideoInfoFingerprint.custom)

        assertEquals("J4", playerTitleStateFingerprint.name)
        assertEquals(
            listOf(
                "LEl/M;",
                "LEl/O;",
                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                "Ljp/nicovideo/nicobox/ui/player/o\$h;"
            ),
            playerTitleStateFingerprint.parameters
        )
        assertNotNull(playerTitleStateFingerprint.custom)
    }
}
