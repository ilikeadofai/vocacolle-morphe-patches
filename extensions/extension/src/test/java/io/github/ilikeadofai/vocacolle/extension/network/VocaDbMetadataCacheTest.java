package io.github.ilikeadofai.vocacolle.extension.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.ilikeadofai.vocacolle.extension.cache.MorpheCache;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VocaDbMetadataCacheTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void positiveAndNotFoundEntriesUseIndependentTtls() throws Exception {
        MutableClock clock = new MutableClock(1000L);
        VocaDbMetadataCache cache = cache(clock, 100L, 10L);

        cache.putSong(song("sm1"));
        cache.putNotFound("sm2");

        VocaDbMetadataCache.Lookup positive = cache.get("sm1");
        VocaDbMetadataCache.Lookup notFound = cache.get("sm2");
        assertNotNull(positive);
        assertEquals(42, positive.song().id());
        assertFalse(positive.isNotFound());
        assertNotNull(notFound);
        assertTrue(notFound.isNotFound());
        assertNull(notFound.song());

        clock.now = 1010L;
        assertNull(cache.get("sm2"));
        assertEquals(42, cache.get("sm1").song().id());

        clock.now = 1100L;
        assertNull(cache.get("sm1"));
    }

    @Test
    public void malformedTypedPayloadIsRemovedAndTreatedAsMiss() throws Exception {
        File root = temporaryFolder.newFolder("corrupt-vocadb");
        MorpheCache rawCache = new MorpheCache(root, 4096, 16384, () -> 1000L);
        rawCache.put(
                VocaDbMetadataCache.NAMESPACE,
                "sm1",
                "not-json".getBytes(StandardCharsets.UTF_8),
                1000L
        );
        VocaDbMetadataCache cache = new VocaDbMetadataCache(rawCache, 100L, 10L);

        assertNull(cache.get("sm1"));
        assertNull(rawCache.get(VocaDbMetadataCache.NAMESPACE, "sm1"));
    }

    private VocaDbMetadataCache cache(MutableClock clock, long positiveTtl, long notFoundTtl)
            throws Exception {
        MorpheCache raw = new MorpheCache(
                temporaryFolder.newFolder("cache-" + clock.now),
                4096,
                16384,
                clock
        );
        return new VocaDbMetadataCache(raw, positiveTtl, notFoundTtl);
    }

    static VocaDbSong song(String mediaId) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("Japanese", "曲名");
        names.put("English", "Song title");
        return new VocaDbSong(
                42,
                "曲名",
                mediaId,
                names,
                Arrays.asList("YoutubeOriginal")
        );
    }

    static final class MutableClock implements MorpheCache.Clock {
        long now;
        MutableClock(long now) { this.now = now; }
        @Override public long nowMillis() { return now; }
    }
}
