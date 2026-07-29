package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.patch.ApkFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VocaColleAdControlPatchTest {
    @Test
    fun `declares an opt-in ad control patch for VocaColle 7_40_0`() {
        val patch = vocacolleAdControlPatch

        assertEquals("VocaColle ad control", patch.name)
        assertFalse(patch.default)
        assertTrue(patch.dependencies.contains(vocacolleMorpheSettingsPatch))

        val compatibility = assertNotNull(patch.compatibility).single()
        assertEquals("jp.nicovideo.nicobox", compatibility.packageName)
        assertEquals(ApkFileType.APK, compatibility.apkFileType)
        assertEquals(listOf("7.40.0"), compatibility.targets.map { it.version })
    }

    @Test
    fun `fingerprints the exact app open ad eligibility coroutine`() {
        assertEquals("Llg/c;", appOpenAdAllowedFingerprint.definingClass)
        assertEquals("n", appOpenAdAllowedFingerprint.name)
        assertEquals("Ljava/lang/Object;", appOpenAdAllowedFingerprint.returnType)
        assertEquals(
            listOf("Landroid/content/Context;", "Lsl/e;"),
            appOpenAdAllowedFingerprint.parameters
        )
        assertEquals(
            listOf(
                "App Open Ad is not allowed. (no user info)",
                "App Open Ad is not allowed. (premium member)"
            ),
            appOpenAdAllowedFingerprint.strings
        )
        assertNotNull(appOpenAdAllowedFingerprint.custom)
    }

    @Test
    fun `fingerprints the central audio ad source decision`() {
        assertEquals("Lwh/b;", audioAdContentFingerprint.definingClass)
        assertEquals("e", audioAdContentFingerprint.name)
        assertEquals("Ljava/lang/Object;", audioAdContentFingerprint.returnType)
        assertEquals(listOf("Z", "Z", "Lsl/e;"), audioAdContentFingerprint.parameters)
        assertNotNull(audioAdContentFingerprint.custom)
    }

    @Test
    fun `fingerprints the central banner and in feed ad loader`() {
        assertEquals("LBj/b;", displayAdLoadFingerprint.definingClass)
        assertEquals("s", displayAdLoadFingerprint.name)
        assertEquals("V", displayAdLoadFingerprint.returnType)
        assertEquals(emptyList(), displayAdLoadFingerprint.parameters)
        assertEquals(listOf("loadAd("), displayAdLoadFingerprint.strings)
        assertNotNull(displayAdLoadFingerprint.custom)
    }

    @Test
    fun `fingerprints the automatic home premium promotion`() {
        assertEquals(
            "Ljp/nicovideo/nicobox/ui/home/HomeFragment;",
            homePremiumPromotionFingerprint.definingClass
        )
        assertEquals("N3", homePremiumPromotionFingerprint.name)
        assertEquals("Lnl/L;", homePremiumPromotionFingerprint.returnType)
        assertEquals(
            listOf(
                "Ljp/nicovideo/nicobox/ui/home/HomeFragment;",
                "LFf/a;"
            ),
            homePremiumPromotionFingerprint.parameters
        )
        assertNotNull(homePremiumPromotionFingerprint.custom)
    }

    @Test
    fun `fingerprints the automatic high quality playback premium snackbar`() {
        assertEquals(
            "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
            highQualityPremiumSnackbarFingerprint.definingClass
        )
        assertEquals("y4", highQualityPremiumSnackbarFingerprint.name)
        assertEquals("Lnl/L;", highQualityPremiumSnackbarFingerprint.returnType)
        assertEquals(
            listOf(
                "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
                "Lwh/l;"
            ),
            highQualityPremiumSnackbarFingerprint.parameters
        )
        assertNotNull(highQualityPremiumSnackbarFingerprint.custom)
    }

    @Test
    fun `fingerprints exactly seven automatic premium lead callsites`() {
        assertEquals(7, premiumPromotionFingerprints.size)
        assertEquals(
            setOf(
                Triple("LZi/r;", "v", "V"),
                Triple("Lbj/t;", "x", "V"),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;",
                    "G2",
                    "Lnl/L;"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/publicmylist/PublicMylistFragment;",
                    "x3",
                    "V"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/player/PlayerFragment\$k;",
                    "b",
                    "V"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;",
                    "q2",
                    "Lnl/L;"
                ),
                Triple(
                    "Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;",
                    "o2",
                    "Lnl/L;"
                )
            ),
            premiumPromotionFingerprints.map {
                Triple(it.definingClass, it.name, it.returnType)
            }.toSet()
        )
        assertTrue(premiumPromotionFingerprints.all { it.custom != null })
    }
}
