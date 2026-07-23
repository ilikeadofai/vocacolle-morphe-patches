package io.github.ilikeadofai.vocacolle.extension.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.ilikeadofai.vocacolle.extension.settings.SettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class AdControlTest {
    @Test
    public void appOpenOverridePreservesOriginalUnlessRuntimeAndBlockingAreEnabled() {
        InMemoryBackend backend = new InMemoryBackend();
        SettingsStore store = new SettingsStore(backend);

        assertNull(AdControl.resolveAppOpenOverride(store));

        store.setAppOpenAdBlockingEnabled(true);
        assertEquals(Boolean.FALSE, AdControl.resolveAppOpenOverride(store));

        store.setRuntimeFeaturesEnabled(false);
        assertNull(AdControl.resolveAppOpenOverride(store));
    }

    @Test
    public void appOpenBlockingDefaultsOffAndPersistsChanges() {
        InMemoryBackend backend = new InMemoryBackend();
        SettingsStore firstProcess = new SettingsStore(backend);

        assertFalse(firstProcess.isAppOpenAdBlockingEnabled());
        firstProcess.setAppOpenAdBlockingEnabled(true);

        assertTrue(new SettingsStore(backend).isAppOpenAdBlockingEnabled());
    }

    @Test
    public void playerAdBlockingRequiresRuntimeAndFeatureToggles() {
        InMemoryBackend backend = new InMemoryBackend();
        SettingsStore store = new SettingsStore(backend);

        assertFalse(AdControl.shouldBlockPlayerAds(store));
        store.setPlayerAdBlockingEnabled(true);
        assertTrue(AdControl.shouldBlockPlayerAds(store));
        store.setRuntimeFeaturesEnabled(false);
        assertFalse(AdControl.shouldBlockPlayerAds(store));

        store.setRuntimeFeaturesEnabled(true);
        assertTrue(new SettingsStore(backend).isPlayerAdBlockingEnabled());
    }

    @Test
    public void premiumPromotionHidingRequiresRuntimeAndFeatureToggles() {
        InMemoryBackend backend = new InMemoryBackend();
        SettingsStore store = new SettingsStore(backend);

        assertFalse(AdControl.shouldHidePremiumPromotions(store));
        store.setPremiumPromotionHidingEnabled(true);
        assertTrue(AdControl.shouldHidePremiumPromotions(store));
        store.setRuntimeFeaturesEnabled(false);
        assertFalse(AdControl.shouldHidePremiumPromotions(store));

        store.setRuntimeFeaturesEnabled(true);
        assertTrue(new SettingsStore(backend).isPremiumPromotionHidingEnabled());
    }

    @Test
    public void settingsReadFailuresPreserveAllOriginalFlows() {
        SettingsStore store = new SettingsStore(new ThrowingBackend());

        assertNull(AdControl.resolveAppOpenOverride(store));
        assertFalse(AdControl.shouldBlockPlayerAds(store));
        assertFalse(AdControl.shouldHidePremiumPromotions(store));
    }

    @Test
    public void coroutineResumeWithNullContextPreservesAppOpenFlow() {
        assertNull(AdControl.appOpenAdOverride(null));
    }

    private static final class InMemoryBackend implements SettingsStore.Backend {
        private final Map<String, Boolean> booleans = new HashMap<>();
        private final Map<String, String> strings = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return booleans.getOrDefault(key, defaultValue);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            booleans.put(key, value);
        }

        @Override
        public String getString(String key, String defaultValue) {
            return strings.getOrDefault(key, defaultValue);
        }

        @Override
        public void putString(String key, String value) {
            strings.put(key, value);
        }
    }

    private static final class ThrowingBackend implements SettingsStore.Backend {
        @Override
        public boolean getBoolean(String key, boolean fallback) {
            throw new IllegalStateException("preferences unavailable");
        }

        @Override
        public String getString(String key, String fallback) {
            throw new IllegalStateException("preferences unavailable");
        }

        @Override
        public void putBoolean(String key, boolean value) {
            throw new IllegalStateException("preferences unavailable");
        }

        @Override
        public void putString(String key, String value) {
            throw new IllegalStateException("preferences unavailable");
        }
    }
}
