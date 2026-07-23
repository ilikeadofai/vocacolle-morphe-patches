package io.github.ilikeadofai.vocacolle.extension.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MorpheSettingsFragmentTest {
    @Test
    public void validLanguageChangePersistsAppliesAndRecreatesHost() {
        TestBackend backend = new TestBackend();
        SettingsStore store = new SettingsStore(backend);
        RecordingActions actions = new RecordingActions();

        assertTrue(MorpheSettingsFragment.handleDisplayLanguageChange("en", store, actions));
        assertEquals(DisplayLanguage.ENGLISH, store.getDisplayLanguage());
        assertEquals(DisplayLanguage.ENGLISH, actions.appliedLanguage);
        assertTrue(actions.recreated);
    }

    @Test
    public void nonStringLanguageChangeIsRejectedWithoutRecreate() {
        RecordingActions actions = new RecordingActions();

        assertFalse(MorpheSettingsFragment.handleDisplayLanguageChange(
                Boolean.TRUE,
                new SettingsStore(new TestBackend()),
                actions
        ));
        assertFalse(actions.recreated);
    }

    @Test
    public void failedLanguageApplyDoesNotPersistOrRecreate() {
        SettingsStore store = new SettingsStore(new TestBackend());
        store.setDisplayLanguage(DisplayLanguage.JAPANESE);
        RecordingActions actions = new RecordingActions(true);

        assertFalse(MorpheSettingsFragment.handleDisplayLanguageChange("en", store, actions));
        assertEquals(DisplayLanguage.JAPANESE, store.getDisplayLanguage());
        assertFalse(actions.recreated);
    }

    @Test
    public void formatsCacheSizesForCompactSettingsSummaries() {
        assertEquals("0 B", MorpheSettingsFragment.formatCacheSize(0));
        assertEquals("1.0 KiB", MorpheSettingsFragment.formatCacheSize(1024));
        assertEquals("1.5 MiB", MorpheSettingsFragment.formatCacheSize(1572864));
    }

    @Test
    public void cacheClearReportsSuccessAndFailureWithoutThrowing() {
        RecordingCacheActions success = new RecordingCacheActions(false);
        RecordingCacheActions failure = new RecordingCacheActions(true);

        assertTrue(MorpheSettingsFragment.handleClearCache(success));
        assertTrue(success.cleared);
        assertFalse(MorpheSettingsFragment.handleClearCache(failure));
    }

    private static final class RecordingActions
            implements MorpheSettingsFragment.LanguageChangeActions {
        private DisplayLanguage appliedLanguage;
        private boolean recreated;
        private final boolean failApply;

        private RecordingActions() {
            this(false);
        }

        private RecordingActions(boolean failApply) {
            this.failApply = failApply;
        }

        @Override
        public void apply(DisplayLanguage language) {
            if (failApply) {
                throw new IllegalStateException("expected");
            }
            appliedLanguage = language;
        }

        @Override
        public void recreateHost() {
            recreated = true;
        }
    }

    private static final class TestBackend implements SettingsStore.Backend {
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

    private static final class RecordingCacheActions
            implements MorpheSettingsFragment.CacheActions {
        private final boolean fail;
        private boolean cleared;

        private RecordingCacheActions(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void clear() throws IOException {
            if (fail) {
                throw new IOException("expected");
            }
            cleared = true;
        }
    }
}