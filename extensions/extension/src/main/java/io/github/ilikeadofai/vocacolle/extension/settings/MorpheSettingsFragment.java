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
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Preferences for runtime features supplied by the VocaColle Morphe extension. */
@SuppressWarnings("deprecation")
public final class MorpheSettingsFragment extends PreferenceFragment {
    private static final ThreadPoolExecutor CACHE_EXECUTOR = createCacheExecutor();

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
        final CacheIoActions cacheActions = cache == null ? null : new CacheIoActions() {
            @Override
            public long sizeBytes() throws IOException {
                return cache.sizeBytes();
            }

            @Override
            public void clear() throws IOException {
                cache.clear();
            }
        };
        Preference cacheInfo = new Preference(activity);
        cacheInfo.setTitle(strings.cacheTitle);
        cacheInfo.setSelectable(false);
        if (cacheActions == null) {
            cacheInfo.setSummary(strings.cacheClearFailedMessage);
        } else {
            cacheInfo.setSummary(formatCacheSummary(0L, strings));
            loadCacheSizeAsync(
                    CACHE_EXECUTOR,
                    weakUiExecutor(activity),
                    cacheActions,
                    new CacheUiResult(this, activity, cacheInfo, null, strings, false)
            );
        }
        storage.addPreference(cacheInfo);

        Preference clearCache = new Preference(activity);
        clearCache.setTitle(strings.clearCacheTitle);
        clearCache.setSummary(strings.clearCacheSummary);
        clearCache.setEnabled(cacheActions != null);
        clearCache.setOnPreferenceClickListener(preference -> {
            if (cacheActions == null) return true;
            clearCache.setEnabled(false);
            clearCacheAsync(
                    CACHE_EXECUTOR,
                    weakUiExecutor(activity),
                    cacheActions,
                    new CacheUiResult(this, activity, cacheInfo, clearCache, strings, true)
            );
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

    static ThreadPoolExecutor createCacheExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8),
                task -> {
                    Thread thread = new Thread(task, "morphe-cache-io");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static Executor weakUiExecutor(Activity activity) {
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        return task -> {
            Activity currentActivity = activityReference.get();
            if (currentActivity != null) currentActivity.runOnUiThread(task);
        };
    }

    static void loadCacheSizeAsync(
            Executor backgroundExecutor,
            Executor uiExecutor,
            CacheIoActions cache,
            CacheResultActions result
    ) {
        runCacheOperationAsync(backgroundExecutor, uiExecutor, cache, result, false);
    }

    static void clearCacheAsync(
            Executor backgroundExecutor,
            Executor uiExecutor,
            CacheIoActions cache,
            CacheResultActions result
    ) {
        runCacheOperationAsync(backgroundExecutor, uiExecutor, cache, result, true);
    }

    private static void runCacheOperationAsync(
            Executor backgroundExecutor,
            Executor uiExecutor,
            CacheIoActions cache,
            CacheResultActions result,
            boolean clearFirst
    ) {
        Runnable work = () -> {
            boolean success = true;
            if (clearFirst) {
                try {
                    cache.clear();
                } catch (IOException | RuntimeException failure) {
                    success = false;
                }
            }
            long sizeBytes = 0L;
            try {
                sizeBytes = cache.sizeBytes();
            } catch (IOException | RuntimeException failure) {
                success = false;
            }
            final boolean completedSuccessfully = success;
            final long completedSizeBytes = sizeBytes;
            uiExecutor.execute(() -> result.complete(completedSuccessfully, completedSizeBytes));
        };
        try {
            backgroundExecutor.execute(work);
        } catch (RejectedExecutionException rejected) {
            uiExecutor.execute(() -> result.complete(false, 0L));
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

    private static String formatCacheSummary(long sizeBytes, MorpheSettingsStrings strings) {
        return String.format(
                Locale.ROOT,
                strings.cacheSummaryFormat,
                formatCacheSize(sizeBytes),
                formatCacheSize(MorpheCache.DEFAULT_MAX_TOTAL_BYTES)
        );
    }

    private static final class CacheUiResult implements CacheResultActions {
        private final WeakReference<MorpheSettingsFragment> fragmentReference;
        private final WeakReference<Activity> activityReference;
        private final WeakReference<Preference> cacheInfoReference;
        private final WeakReference<Preference> clearCacheReference;
        private final MorpheSettingsStrings strings;
        private final boolean showToast;

        private CacheUiResult(
                MorpheSettingsFragment fragment,
                Activity activity,
                Preference cacheInfo,
                Preference clearCache,
                MorpheSettingsStrings strings,
                boolean showToast
        ) {
            fragmentReference = new WeakReference<>(fragment);
            activityReference = new WeakReference<>(activity);
            cacheInfoReference = new WeakReference<>(cacheInfo);
            clearCacheReference = new WeakReference<>(clearCache);
            this.strings = strings;
            this.showToast = showToast;
        }

        @Override
        public void complete(boolean success, long sizeBytes) {
            MorpheSettingsFragment fragment = fragmentReference.get();
            Activity activity = activityReference.get();
            Preference cacheInfo = cacheInfoReference.get();
            if (fragment == null || activity == null || cacheInfo == null
                    || fragment.getActivity() != activity) {
                return;
            }
            cacheInfo.setSummary(success || showToast
                    ? formatCacheSummary(sizeBytes, strings)
                    : strings.cacheClearFailedMessage);
            Preference clearCache = clearCacheReference.get();
            if (clearCache != null) clearCache.setEnabled(true);
            if (showToast) {
                Toast.makeText(
                        activity,
                        success ? strings.cacheClearedMessage : strings.cacheClearFailedMessage,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    interface LanguageChangeActions {
        void apply(DisplayLanguage language);

        void recreateHost();
    }

    interface CacheActions {
        void clear() throws IOException;
    }

    interface CacheIoActions {
        long sizeBytes() throws IOException;

        void clear() throws IOException;
    }

    interface CacheResultActions {
        void complete(boolean success, long sizeBytes);
    }
}
