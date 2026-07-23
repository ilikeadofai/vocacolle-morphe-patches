package io.github.ilikeadofai.vocacolle.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

/** Entry point called from the patched VocaColle settings menu provider. */
public final class MorpheSettingsLauncher {
    private static final String MORPHE_MENU_TITLE = "Morphe";
    // Reserved programmatic ID used only by the injected VocaColle settings menu item.
    private static final int MORPHE_MENU_ITEM_ID = 0x4d4f5250;

    private MorpheSettingsLauncher() {
    }

    @SuppressWarnings("unused")
    public static void addMorpheMenuItem(Menu menu) {
        if (menu == null || menu.findItem(MORPHE_MENU_ITEM_ID) != null) {
            return;
        }
        MenuItem item = menu.add(Menu.NONE, MORPHE_MENU_ITEM_ID, Menu.NONE, MORPHE_MENU_TITLE);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @SuppressWarnings("unused")
    public static boolean isMorpheMenuItem(MenuItem item) {
        return item != null && isMorpheMenuItemId(item.getItemId());
    }

    public static boolean isMorpheMenuItemId(int itemId) {
        return itemId == MORPHE_MENU_ITEM_ID;
    }

    @SuppressWarnings("unused")
    public static void open(Context context) {
        if (context == null) {
            return;
        }

        android.content.Intent intent = MorpheSettingsActivity.createIntent(context);
        if (!(context instanceof Activity)) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
