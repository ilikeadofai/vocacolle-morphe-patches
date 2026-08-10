package io.github.ilikeadofai.vocacolle.extension.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Test;

public class PlayerTitleResolverTest {
    @Test
    public void originalRemainsUntilBackgroundAndUiStagesComplete() {
        QueuedExecutor background = new QueuedExecutor();
        QueuedExecutor ui = new QueuedExecutor();
        RecordingTarget target = new RecordingTarget("sm1");
        PlayerTitleResolver resolver = new PlayerTitleResolver(
                (mediaId, originalTitle) -> "Song title",
                background,
                ui
        );

        resolver.resolve("sm1", "原題", target);
        assertNull(target.appliedTitle);
        background.runNext();
        assertNull(target.appliedTitle);
        ui.runNext();

        assertEquals("Song title", target.appliedTitle);
        assertEquals("sm1", target.appliedMediaId);
    }

    @Test
    public void staleRecycledTargetRejectsCompletedLookup() {
        QueuedExecutor background = new QueuedExecutor();
        QueuedExecutor ui = new QueuedExecutor();
        RecordingTarget target = new RecordingTarget("sm1");
        PlayerTitleResolver resolver = new PlayerTitleResolver(
                (mediaId, originalTitle) -> "Song title",
                background,
                ui
        );

        resolver.resolve("sm1", "原題", target);
        background.runNext();
        target.boundMediaId = "sm2";
        ui.runNext();

        assertNull(target.appliedTitle);
    }

    @Test
    public void lookupFailureAndNoAlternativeTitleLeaveOriginalUntouched() {
        QueuedExecutor background = new QueuedExecutor();
        QueuedExecutor ui = new QueuedExecutor();
        RecordingTarget failureTarget = new RecordingTarget("sm1");
        PlayerTitleResolver.TitleLookup failing = (mediaId, originalTitle) -> {
            throw new IOException("offline");
        };
        new PlayerTitleResolver(failing, background, ui)
                .resolve("sm1", "原題", failureTarget);
        background.runNext();
        assertNull(failureTarget.appliedTitle);
        assertEquals(0, ui.size());

        RecordingTarget fallbackTarget = new RecordingTarget("sm2");
        new PlayerTitleResolver((mediaId, originalTitle) -> originalTitle, background, ui)
                .resolve("sm2", "原題", fallbackTarget);
        background.runNext();
        assertNull(fallbackTarget.appliedTitle);
        assertEquals(0, ui.size());
    }

    @Test
    public void permissionRevokedWhileQueuedPreventsLookup() {
        QueuedExecutor background = new QueuedExecutor();
        QueuedExecutor ui = new QueuedExecutor();
        int[] lookupCalls = {0};
        boolean[] allowed = {true};
        PlayerTitleResolver resolver = new PlayerTitleResolver(
                (mediaId, originalTitle) -> {
                    lookupCalls[0]++;
                    return "English title";
                },
                background,
                ui,
                mediaId -> allowed[0]
        );

        resolver.resolve("sm9", "Original", new RecordingTarget("sm9"));
        allowed[0] = false;
        background.runNext();

        assertEquals(0, lookupCalls[0]);
        assertEquals(0, ui.size());
    }

    @Test
    public void productionLookupExecutorHasFiniteCapacity() {
        ThreadPoolExecutor executor = PlayerTitleEnrichment.createLookupExecutor();
        try {
            assertTrue(executor.getQueue().remainingCapacity() < Integer.MAX_VALUE);
            assertTrue(executor.getMaximumPoolSize() <= 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void resolvedTitleIsReusedSynchronouslyForLaterBindings() {
        PlayerTitleEnrichment.rememberResolvedTitle("sm-memory-test", "English title");

        assertEquals(
                "English title",
                PlayerTitleEnrichment.rememberedTitle("sm-memory-test", "原題")
        );
        assertNull(PlayerTitleEnrichment.rememberedTitle("sm-memory-miss", "原題"));
        assertNull(PlayerTitleEnrichment.rememberedTitle("sm-memory-test", "English title"));
    }

    private static final class RecordingTarget implements PlayerTitleResolver.Target {
        String boundMediaId;
        String appliedMediaId;
        String appliedTitle;

        RecordingTarget(String boundMediaId) {
            this.boundMediaId = boundMediaId;
        }

        @Override public boolean isBoundTo(String mediaId) {
            return mediaId.equals(boundMediaId);
        }

        @Override public void applyTitle(String mediaId, String title) {
            appliedMediaId = mediaId;
            appliedTitle = title;
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove().run(); }
        int size() { return tasks.size(); }
    }
}
