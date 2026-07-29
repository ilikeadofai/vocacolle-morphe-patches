package io.github.ilikeadofai.vocacolle.extension.metadata;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerTitleBridgeTest {
    @Test
    public void bindingKeyChangesWhenEitherIdentityOrDisplayTitleChanges() {
        assertEquals("sm1\u0000Original", PlayerTitleBridge.bindingKey("sm1", "Original"));
        assertEquals("sm1\u0000English", PlayerTitleBridge.bindingKey("sm1", "English"));
        assertEquals("sm2\u0000English", PlayerTitleBridge.bindingKey("sm2", "English"));
    }

    @Test
    public void publishCallsOnlyExactPlayerViewModelBoundary() {
        RecordingViewModel viewModel = new RecordingViewModel();

        PlayerTitleBridge.publish(viewModel, "English", "owner", "sm1", 3, true);

        assertEquals("English", viewModel.title);
        assertEquals("owner", viewModel.owner);
        assertEquals("sm1", viewModel.mediaId);
        assertEquals(3, viewModel.position);
        assertEquals(true, viewModel.masked);
    }

    @Test
    public void missingBoundaryFailsClosedWithoutThrowing() {
        PlayerTitleBridge.publish(new Object(), "English", "owner", "sm1", 3, false);
    }

    public static final class RecordingViewModel {
        String title;
        String owner;
        String mediaId;
        int position;
        boolean masked;

        public void h2(
                String title,
                String owner,
                String mediaId,
                int position,
                boolean masked
        ) {
            this.title = title;
            this.owner = owner;
            this.mediaId = mediaId;
            this.position = position;
            this.masked = masked;
        }
    }
}
