package io.github.ilikeadofai.vocacolle.extension.ads;

import android.content.Context;
import io.github.ilikeadofai.vocacolle.extension.settings.SettingsStore;

/** Runtime policy boundary for VocaColle advertising hooks. */
public final class AdControl {
    private static volatile SettingsStore settingsStore;
    private static volatile boolean hooksInstalled;

    private AdControl() {
    }

    /**
     * Returns {@code null} to preserve VocaColle's original app-open decision, or
     * {@link Boolean#FALSE} to stop before the ad provider starts loading.
     */
    public static Boolean appOpenAdOverride(Context context) {
        try {
            return resolveAppOpenOverride(SettingsStore.from(context));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static void initialize(Context context) {
        try {
            settingsStore = SettingsStore.from(context);
            hooksInstalled = true;
        } catch (RuntimeException ignored) {
            settingsStore = null;
            hooksInstalled = false;
        }
    }

    public static boolean areHooksInstalled() {
        return hooksInstalled;
    }

    public static boolean shouldBlockPlayerAds() {
        SettingsStore store = settingsStore;
        return store != null && shouldBlockPlayerAds(store);
    }

    public static boolean shouldBlockPlayerAds(Context context) {
        try {
            return shouldBlockPlayerAds(SettingsStore.from(context));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean shouldBlockDisplayAds(Context context) {
        try {
            return shouldBlockDisplayAds(SettingsStore.from(context));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean shouldHidePremiumPromotions() {
        SettingsStore store = settingsStore;
        return store != null && shouldHidePremiumPromotions(store);
    }

    static Boolean resolveAppOpenOverride(SettingsStore store) {
        try {
            if (!store.areRuntimeFeaturesEnabled() || !store.isAppOpenAdBlockingEnabled()) {
                return null;
            }
            return Boolean.FALSE;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean shouldBlockPlayerAds(SettingsStore store) {
        try {
            return store.areRuntimeFeaturesEnabled() && store.isPlayerAdBlockingEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean shouldBlockDisplayAds(SettingsStore store) {
        try {
            return store.areRuntimeFeaturesEnabled() && store.isDisplayAdBlockingEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean shouldHidePremiumPromotions(SettingsStore store) {
        try {
            return store.areRuntimeFeaturesEnabled() && store.isPremiumPromotionHidingEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
