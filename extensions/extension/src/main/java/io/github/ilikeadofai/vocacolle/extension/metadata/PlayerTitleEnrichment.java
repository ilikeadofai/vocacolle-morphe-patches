package io.github.ilikeadofai.vocacolle.extension.metadata;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import io.github.ilikeadofai.vocacolle.extension.cache.MorpheCache;
import io.github.ilikeadofai.vocacolle.extension.network.MorpheHttpClient;
import io.github.ilikeadofai.vocacolle.extension.network.VocaDbClient;
import io.github.ilikeadofai.vocacolle.extension.network.VocaDbMetadataCache;
import io.github.ilikeadofai.vocacolle.extension.network.VocaDbRepository;
import io.github.ilikeadofai.vocacolle.extension.network.VocaDbSong;
import io.github.ilikeadofai.vocacolle.extension.network.VocaDbTitleSelector;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Asynchronously enriches only the currently bound full-player title. */
public final class PlayerTitleEnrichment {
    private static final ThreadPoolExecutor LOOKUP_EXECUTOR = createLookupExecutor();
    private static final Map<View, Binding> BINDINGS = Collections.synchronizedMap(
            new WeakHashMap<>()
    );
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    // ponytail: process-lifetime cache; bound it only if real long-running sessions show growth.
    private static final ConcurrentHashMap<String, String> RESOLVED_TITLES = new ConcurrentHashMap<>();
    private static volatile VocaDbRepository repository;

    private PlayerTitleEnrichment() { }

    public static void onBound(
            View anchor,
            Object playerViewModel,
            String originalTitle,
            String ownerName,
            String mediaId,
            int position,
            boolean sensitiveMaskingRequired
    ) {
        if (anchor == null || playerViewModel == null || mediaId == null || originalTitle == null) {
            return;
        }
        BINDINGS.put(anchor, new Binding(
                playerViewModel,
                mediaId,
                originalTitle,
                ownerName,
                position,
                sensitiveMaskingRequired
        ));
        if (!MetadataControl.shouldEnrichPlayerTitles(anchor.getContext())) {
            return;
        }
        String remembered = rememberedTitle(mediaId, originalTitle);
        if (remembered != null) {
            PlayerTitleBridge.publish(
                    playerViewModel,
                    remembered,
                    ownerName,
                    mediaId,
                    position,
                    sensitiveMaskingRequired
            );
            return;
        }
        if (IN_FLIGHT.putIfAbsent(mediaId, Boolean.TRUE) != null) {
            return;
        }

        Context applicationContext = anchor.getContext().getApplicationContext();
        Context storageContext = applicationContext != null
                ? applicationContext
                : anchor.getContext();
        PlayerTitleResolver resolver = new PlayerTitleResolver(
                (requestedMediaId, sourceTitle) -> {
                    try {
                        VocaDbSong song = repository(storageContext)
                                .lookupByNicoVideoId(requestedMediaId);
                        return VocaDbTitleSelector.select(song, sourceTitle);
                    } finally {
                        IN_FLIGHT.remove(requestedMediaId);
                    }
                },
                LOOKUP_EXECUTOR,
                task -> {
                    if (!MainHandlerHolder.INSTANCE.post(task)) {
                        throw new IllegalStateException("Player title UI queue is unavailable");
                    }
                },
                PlayerTitleEnrichment::permitLookup
        );
        boolean accepted = resolver.resolve(mediaId, originalTitle, new PlayerTitleResolver.Target() {
            @Override
            public boolean isBoundTo(String completedMediaId) {
                return hasActiveBinding(completedMediaId);
            }

            @Override
            public void applyTitle(String completedMediaId, String title) {
                applyResolvedTitle(completedMediaId, title);
            }
        });
        if (!accepted) {
            IN_FLIGHT.remove(mediaId);
        }
    }

    public static void restoreOriginalTitles() {
        List<Map.Entry<View, Binding>> bindings;
        synchronized (BINDINGS) {
            bindings = new ArrayList<>(BINDINGS.entrySet());
        }
        for (Map.Entry<View, Binding> entry : bindings) {
            View anchor = entry.getKey();
            Binding binding = entry.getValue();
            if (anchor == null || binding == null) {
                continue;
            }
            anchor.post(() -> {
                if (!anchor.isAttachedToWindow() || BINDINGS.get(anchor) != binding) {
                    return;
                }
                PlayerTitleBridge.publish(
                        binding.viewModel.get(),
                        binding.originalTitle,
                        binding.ownerName,
                        binding.mediaId,
                        binding.position,
                        binding.sensitiveMaskingRequired
                );
            });
        }
    }

    static ThreadPoolExecutor createLookupExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                2,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static VocaDbRepository repository(Context context) throws IOException {
        VocaDbRepository current = repository;
        if (current != null) {
            return current;
        }
        synchronized (PlayerTitleEnrichment.class) {
            current = repository;
            if (current == null) {
                MorpheCache cache = MorpheCache.openDefault(context.getCacheDir());
                current = new VocaDbRepository(
                        new VocaDbClient(new MorpheHttpClient()),
                        new VocaDbMetadataCache(cache)
                );
                repository = current;
            }
            return current;
        }
    }

    private static boolean hasActiveBinding(String mediaId) {
        synchronized (BINDINGS) {
            for (Map.Entry<View, Binding> entry : BINDINGS.entrySet()) {
                View anchor = entry.getKey();
                Binding binding = entry.getValue();
                if (anchor != null
                        && binding != null
                        && mediaId.equals(binding.mediaId)
                        && anchor.isAttachedToWindow()
                        && MetadataControl.shouldEnrichPlayerTitles(anchor.getContext())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLookupAllowed(String mediaId) {
        synchronized (BINDINGS) {
            for (Map.Entry<View, Binding> entry : BINDINGS.entrySet()) {
                View anchor = entry.getKey();
                Binding binding = entry.getValue();
                if (anchor != null
                        && binding != null
                        && mediaId.equals(binding.mediaId)
                        && MetadataControl.shouldEnrichPlayerTitles(anchor.getContext())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean permitLookup(String mediaId) {
        boolean allowed = isLookupAllowed(mediaId);
        if (!allowed) {
            IN_FLIGHT.remove(mediaId);
        }
        return allowed;
    }

    private static void applyResolvedTitle(String mediaId, String title) {
        rememberResolvedTitle(mediaId, title);
        List<Map.Entry<View, Binding>> bindings;
        synchronized (BINDINGS) {
            bindings = new ArrayList<>(BINDINGS.entrySet());
        }
        for (Map.Entry<View, Binding> entry : bindings) {
            View anchor = entry.getKey();
            Binding binding = entry.getValue();
            if (anchor == null
                    || binding == null
                    || !mediaId.equals(binding.mediaId)
                    || !anchor.isAttachedToWindow()
                    || !MetadataControl.shouldEnrichPlayerTitles(anchor.getContext())) {
                continue;
            }
            PlayerTitleBridge.publish(
                    binding.viewModel.get(),
                    title,
                    binding.ownerName,
                    binding.mediaId,
                    binding.position,
                    binding.sensitiveMaskingRequired
            );
        }
    }

    static void rememberResolvedTitle(String mediaId, String title) {
        if (mediaId != null && title != null && !title.isEmpty()) {
            RESOLVED_TITLES.put(mediaId, title);
        }
    }

    static String rememberedTitle(String mediaId, String originalTitle) {
        String title = RESOLVED_TITLES.get(mediaId);
        return title == null || title.equals(originalTitle) ? null : title;
    }

    private static final class Binding {
        final WeakReference<Object> viewModel;
        final String mediaId;
        final String originalTitle;
        final String ownerName;
        final int position;
        final boolean sensitiveMaskingRequired;

        Binding(
                Object viewModel,
                String mediaId,
                String originalTitle,
                String ownerName,
                int position,
                boolean sensitiveMaskingRequired
        ) {
            this.viewModel = new WeakReference<>(viewModel);
            this.mediaId = mediaId;
            this.originalTitle = originalTitle;
            this.ownerName = ownerName;
            this.position = position;
            this.sensitiveMaskingRequired = sensitiveMaskingRequired;
        }
    }

    private static final class MainHandlerHolder {
        static final Handler INSTANCE = new Handler(Looper.getMainLooper());

        private MainHandlerHolder() { }
    }
}
