package io.github.ilikeadofai.vocacolle.extension.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MorphePatchInfoTest {
    @Test
    public void appVersionIncludesTheCurrentMorphePatchVersion() {
        assertEquals(
                "7.40.0 · Morphe 1.1.0-dev.1",
                MorphePatchInfo.formatAppVersion("7.40.0")
        );
    }
}
