package io.github.ilikeadofai.vocacolle.extension.metadata;

import java.lang.reflect.Method;

/** Narrow version-bound bridge back into the player title presentation state. */
public final class PlayerTitleBridge {
    private PlayerTitleBridge() { }

    public static String bindingKey(String mediaId, String displayTitle) {
        return String.valueOf(mediaId) + '\u0000' + String.valueOf(displayTitle);
    }

    static void publish(
            Object playerViewModel,
            String title,
            String ownerName,
            String mediaId,
            int position,
            boolean sensitiveMaskingRequired
    ) {
        if (playerViewModel == null || title == null || mediaId == null) {
            return;
        }
        try {
            Method boundary = playerViewModel.getClass().getMethod(
                    "h2",
                    String.class,
                    String.class,
                    String.class,
                    int.class,
                    boolean.class
            );
            boundary.invoke(
                    playerViewModel,
                    title,
                    ownerName,
                    mediaId,
                    position,
                    sensitiveMaskingRequired
            );
        } catch (ReflectiveOperationException | RuntimeException unavailableBoundary) {
            // The original title remains visible when the exact target boundary is unavailable.
        }
    }
}
