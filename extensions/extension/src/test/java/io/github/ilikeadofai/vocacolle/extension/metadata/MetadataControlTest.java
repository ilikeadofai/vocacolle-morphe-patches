package io.github.ilikeadofai.vocacolle.extension.metadata;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.ilikeadofai.vocacolle.extension.settings.SettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MetadataControlTest {
    @Test
    public void enrichmentRequiresMasterAndFeatureToggles() {
        InMemoryBackend backend = new InMemoryBackend();
        SettingsStore store = new SettingsStore(backend);

        assertFalse(MetadataControl.shouldEnrichPlayerTitles(store));
        store.setVocaDbMetadataEnrichmentEnabled(true);
        assertTrue(MetadataControl.shouldEnrichPlayerTitles(store));
        store.setRuntimeFeaturesEnabled(false);
        assertFalse(MetadataControl.shouldEnrichPlayerTitles(store));
        store.setRuntimeFeaturesEnabled(true);
        store.setVocaDbMetadataEnrichmentEnabled(false);
        assertFalse(MetadataControl.shouldEnrichPlayerTitles(store));
    }

    @Test
    public void settingsFailureFailsClosed() {
        SettingsStore failingStore = new SettingsStore(new SettingsStore.Backend() {
            @Override public boolean getBoolean(String key, boolean defaultValue) {
                throw new IllegalStateException("broken preferences");
            }
            @Override public void putBoolean(String key, boolean value) { }
            @Override public String getString(String key, String defaultValue) { return defaultValue; }
            @Override public void putString(String key, String value) { }
        });

        assertFalse(MetadataControl.shouldEnrichPlayerTitles(failingStore));
    }

    private static final class InMemoryBackend implements SettingsStore.Backend {
        private final Map<String, Boolean> values = new HashMap<>();
        @Override public boolean getBoolean(String key, boolean defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }
        @Override public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }
        @Override public String getString(String key, String defaultValue) { return defaultValue; }
        @Override public void putString(String key, String value) { }
    }
}
