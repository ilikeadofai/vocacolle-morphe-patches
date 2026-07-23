package io.github.ilikeadofai.vocacolle.extension.settings;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.widget.Toast;
import io.github.ilikeadofai.vocacolle.extension.cache.MorpheCache;
import java.io.IOException;
import java.util.Locale;

/** Preferences for runtime features supplied by the VocaColle Morphe extension. */
@SuppressWarnings("deprecation")
public final class MorpheSettingsFragment extends PreferenceFragment {
    private SettingsStore settingsStore;
    private SwitchPreference runtimeFeaturesPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("Morphe settings fragment is not attached");
        }
        settingsStore = SettingsStore.from(activity);

        MorpheSettingsStrings strings = MorpheSettingsStrings.resolve(
                settingsStore.getDisplayLanguage(),
                Locale.getDefault().getLanguage()
        );
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(activity);
        PreferenceCategory general = new PreferenceCategory(activity);
        general.setTitle(strings.generalCategory);
        screen.addPreference(general);

        ListPreference displayLanguagePreference = new ListPreference(activity);
        displayLanguagePreference.setKey(SettingKeys.DISPLAY_LANGUAGE);
        displayLanguagePreference.setTitle(strings.displayLanguageTitle);
        displayLanguagePreference.setEntries(strings.displayLanguageEntries);
        displayLanguagePreference.setEntryValues(strings.displayLanguageEntryValues);
        displayLanguagePreference.setPersistent(false);
        displayLanguagePreference.setValue(settingsStore.getDisplayLanguage().persistedValue());
        displayLanguagePreference.setSummary("%s");
        displayLanguagePreference.setOnPreferenceChangeListener((ignored, newValue) ->
                handleDisplayLanguageChange(
                        newValue,
                        settingsStore,
                        new LanguageChangeActions() {
                            @Override
                            public void apply(DisplayLanguage language) {
                                AppLanguageController.apply(activity, language);
                            }

                            @Override
                            public void recreateHost() {
                                activity.recreate();
                            }
                        }
                )
        );
        general.addPreference(displayLanguagePreference);

        runtimeFeaturesPreference = new SwitchPreference(activity);
        runtimeFeaturesPreference.setKey(SettingKeys.RUNTIME_FEATURES_ENABLED);
        runtimeFeaturesPreference.setTitle(strings.runtimeFeaturesTitle);
        runtimeFeaturesPreference.setSummary(strings.runtimeFeaturesSummary);
        runtimeFeaturesPreference.setDefaultValue(true);
        runtimeFeaturesPreference.setChecked(settingsStore.areRuntimeFeaturesEnabled());
        runtimeFeaturesPreference.setOnPreferenceChangeListener((ignored, newValue) -> {
            if (!(newValue instanceof Boolean)) {
                return false;
            }
            settingsStore.setRuntimeFeaturesEnabled((Boolean) newValue);
            return true;
        });
        general.addPreference(runtimeFeaturesPreference);

        PreferenceCategory storage = new PreferenceCategory(activity);
        storage.setTitle(strings.storageCategory);
        screen.addPreference(storage);

        final MorpheCache cache = openCache(activity);
        Preference cacheInfo = new Preference(activity);
        cacheInfo.setTitle(strings.cacheTitle);
        cacheInfo.setSelectable(false);
        refreshCacheSummary(cacheInfo, cache, strings);
        storage.addPreference(cacheInfo);

        Preference clearCache = new Preference(activity);
        clearCache.setTitle(strings.clearCacheTitle);
        clearCache.setSummary(strings.clearCacheSummary);
        clearCache.setEnabled(cache != null);
        clearCache.setOnPreferenceClickListener(preference -> {
            boolean cleared = cache != null && handleClearCache(cache::clear);
            refreshCacheSummary(cacheInfo, cache, strings);
            Toast.makeText(
                    activity,
                    cleared ? strings.cacheClearedMessage : strings.cacheClearFailedMessage,
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        });
        storage.addPreference(clearCache);

        Preference diagnostics = new Preference(activity);
        diagnostics.setKey("morphe_diagnostics");
        diagnostics.setTitle(strings.diagnosticsTitle);
        diagnostics.setSummary(strings.diagnosticsSummary);
        diagnostics.setSelectable(false);
        general.addPreference(diagnostics);

        setPreferenceScreen(screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (runtimeFeaturesPreference != null && settingsStore != null) {
            runtimeFeaturesPreference.setChecked(settingsStore.areRuntimeFeaturesEnabled());
        }
    }

    static boolean handleDisplayLanguageChange(
            Object newValue,
            SettingsStore store,
            LanguageChangeActions actions
    ) {
        if (!(newValue instanceof String)) {
            return false;
        }
        String persistedValue = (String) newValue;
        DisplayLanguage language = DisplayLanguage.fromPersistedValue(persistedValue);
        if (!language.persistedValue().equals(persistedValue)) {
            return false;
        }
        try {
            actions.apply(language);
        } catch (RuntimeException failure) {
            return false;
        }
        store.setDisplayLanguage(language);
        actions.recreateHost();
        return true;
    }

    static boolean handleClearCache(CacheActions actions) {
        try {
            actions.clear();
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    static String formatCacheSize(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        if (safeBytes < 1024L) {
            return safeBytes + " B";
        }
        if (safeBytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", safeBytes / 1024.0);
        }
        if (safeBytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MiB", safeBytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GiB", safeBytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static MorpheCache openCache(Activity activity) {
        try {
            return MorpheCache.openDefault(activity.getCacheDir());
        } catch (IOException failure) {
            return null;
        }
    }

    private static void refreshCacheSummary(
            Preference cacheInfo,
            MorpheCache cache,
            MorpheSettingsStrings strings
    ) {
        if (cache == null) {
            cacheInfo.setSummary(strings.cacheClearFailedMessage);
            return;
        }
        cacheInfo.setSummary(String.format(
                Locale.ROOT,
                strings.cacheSummaryFormat,
                formatCacheSize(cache.sizeBytes()),
                formatCacheSize(MorpheCache.DEFAULT_MAX_TOTAL_BYTES)
        ));
    }

    interface LanguageChangeActions {
        void apply(DisplayLanguage language);

        void recreateHost();
    }

    interface CacheActions {
        void clear() throws IOException;
    }
}
