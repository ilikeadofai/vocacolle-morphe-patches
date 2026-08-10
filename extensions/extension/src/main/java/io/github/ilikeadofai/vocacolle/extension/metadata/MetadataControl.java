package io.github.ilikeadofai.vocacolle.extension.metadata;

import android.content.Context;
import io.github.ilikeadofai.vocacolle.extension.settings.SettingsStore;
import java.util.Objects;

/** Runtime policy boundary for optional external metadata enrichment. */
public final class MetadataControl {
    private static volatile boolean hooksInstalled;

    private MetadataControl() { }

    public static void markHooksInstalled() {
        hooksInstalled = true;
    }

    public static boolean areHooksInstalled() {
        return hooksInstalled;
    }

    public static boolean shouldEnrichPlayerTitles(Context context) {
        if (!hooksInstalled || context == null) {
            return false;
        }
        try {
            return shouldEnrichPlayerTitles(SettingsStore.from(context));
        } catch (RuntimeException settingsFailure) {
            return false;
        }
    }

    static boolean shouldEnrichPlayerTitles(SettingsStore store) {
        Objects.requireNonNull(store, "store");
        try {
            return store.areRuntimeFeaturesEnabled()
                    && store.isVocaDbMetadataEnrichmentEnabled();
        } catch (RuntimeException settingsFailure) {
            return false;
        }
    }
}
