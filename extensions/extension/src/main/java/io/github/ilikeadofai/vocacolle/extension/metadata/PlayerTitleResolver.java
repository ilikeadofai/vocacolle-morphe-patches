package io.github.ilikeadofai.vocacolle.extension.metadata;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Pure asynchronous title enrichment flow, separated from Android views. */
final class PlayerTitleResolver {
    interface TitleLookup {
        String lookupTitle(String mediaId, String originalTitle) throws IOException;
    }

    interface LookupPermission {
        boolean isAllowed(String mediaId);
    }

    interface Target {
        boolean isBoundTo(String mediaId);
        void applyTitle(String mediaId, String title);
    }

    private final TitleLookup titleLookup;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final LookupPermission lookupPermission;

    PlayerTitleResolver(
            TitleLookup titleLookup,
            Executor backgroundExecutor,
            Executor uiExecutor
    ) {
        this(titleLookup, backgroundExecutor, uiExecutor, mediaId -> true);
    }

    PlayerTitleResolver(
            TitleLookup titleLookup,
            Executor backgroundExecutor,
            Executor uiExecutor,
            LookupPermission lookupPermission
    ) {
        this.titleLookup = Objects.requireNonNull(titleLookup, "titleLookup");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        this.lookupPermission = Objects.requireNonNull(lookupPermission, "lookupPermission");
    }

    boolean resolve(String mediaId, String originalTitle, Target target) {
        if (mediaId == null || mediaId.trim().isEmpty()
                || originalTitle == null || originalTitle.trim().isEmpty()
                || target == null) {
            return false;
        }
        try {
            backgroundExecutor.execute(() -> resolveInBackground(mediaId, originalTitle, target));
            return true;
        } catch (RuntimeException rejectedOrBrokenExecutor) {
            // Keep the title already rendered by VocaColle.
            return false;
        }
    }

    private void resolveInBackground(String mediaId, String originalTitle, Target target) {
        final String resolvedTitle;
        try {
            if (!lookupPermission.isAllowed(mediaId)) {
                return;
            }
            resolvedTitle = titleLookup.lookupTitle(mediaId, originalTitle);
        } catch (IOException | RuntimeException lookupFailure) {
            return;
        }
        if (resolvedTitle == null || resolvedTitle.trim().isEmpty()
                || originalTitle.equals(resolvedTitle)) {
            return;
        }
        try {
            uiExecutor.execute(() -> {
                if (target.isBoundTo(mediaId)) {
                    target.applyTitle(mediaId, resolvedTitle);
                }
            });
        } catch (RuntimeException rejectedOrBrokenUiExecutor) {
            // Keep the original title.
        }
    }
}
