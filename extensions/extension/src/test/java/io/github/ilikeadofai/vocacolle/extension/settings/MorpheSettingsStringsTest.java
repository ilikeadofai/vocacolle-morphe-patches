package io.github.ilikeadofai.vocacolle.extension.settings;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class MorpheSettingsStringsTest {
    @Test
    public void selectsKoreanEnglishAndJapaneseFallbackCatalogs() {
        assertEquals(
                "Morphe 설정",
                MorpheSettingsStrings.forLanguage("ko").settingsTitle
        );
        assertEquals(
                "Morphe Settings",
                MorpheSettingsStrings.forLanguage("en").settingsTitle
        );
        assertEquals(
                "Morphe 設定",
                MorpheSettingsStrings.forLanguage("ja").settingsTitle
        );
        assertEquals(
                "Morphe 設定",
                MorpheSettingsStrings.forLanguage("fr").settingsTitle
        );
    }

    @Test
    public void selectedLanguageOverridesSystemAndSystemUsesSystemLocale() {
        assertEquals(
                "Morphe Settings",
                MorpheSettingsStrings.resolve(DisplayLanguage.ENGLISH, "ko").settingsTitle
        );
        assertEquals(
                "Morphe 설정",
                MorpheSettingsStrings.resolve(DisplayLanguage.SYSTEM, "ko").settingsTitle
        );
        assertEquals(
                "Morphe 設定",
                MorpheSettingsStrings.resolve(DisplayLanguage.SYSTEM, "fr").settingsTitle
        );
    }

    @Test
    public void exposesLocalizedDisplayLanguageLabelsInStableValueOrder() {
        MorpheSettingsStrings strings =
                MorpheSettingsStrings.resolve(DisplayLanguage.ENGLISH, "ja");

        assertEquals("Display language", strings.displayLanguageTitle);
        assertEquals(
                Arrays.asList("System default", "Japanese", "English", "Korean"),
                Arrays.asList(strings.displayLanguageEntries)
        );
        assertEquals(
                Arrays.asList("system", "ja", "en", "ko"),
                Arrays.asList(strings.displayLanguageEntryValues)
        );
        assertEquals("Storage", strings.storageCategory);
        assertEquals("Ads", strings.adsCategory);
        assertEquals("Block app-open ads", strings.appOpenAdBlockingTitle);
        assertEquals(
                "Blocks ads shown at cold start and when returning to the app.",
                strings.appOpenAdBlockingSummary
        );
        assertEquals("Block in-player audio ads", strings.playerAdBlockingTitle);
        assertEquals("Hide automatic Premium promotions", strings.premiumPromotionHidingTitle);
        assertEquals("Cached data", strings.cacheTitle);
        assertEquals("Clear cache", strings.clearCacheTitle);
        assertEquals("Cache cleared", strings.cacheClearedMessage);
    }
}
