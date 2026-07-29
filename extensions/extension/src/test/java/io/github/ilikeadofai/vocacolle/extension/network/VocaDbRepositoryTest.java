package io.github.ilikeadofai.vocacolle.extension.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import io.github.ilikeadofai.vocacolle.extension.cache.MorpheCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VocaDbRepositoryTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validPositiveCacheHitWorksOfflineWithoutNetworkCall() throws Exception {
        VocaDbMetadataCache cache = cache();
        cache.putSong(VocaDbMetadataCacheTest.song("sm1"));
        int[] networkCalls = {0};
        VocaDbLookup offline = mediaId -> {
            networkCalls[0]++;
            throw new IOException("offline");
        };

        VocaDbSong song = new VocaDbRepository(offline, cache).lookupByNicoVideoId("sm1");

        assertEquals(42, song.id());
        assertEquals(0, networkCalls[0]);
    }

    @Test
    public void cacheMissFetchesOnceThenReusesPositiveEntry() throws Exception {
        VocaDbMetadataCache cache = cache();
        int[] networkCalls = {0};
        VocaDbLookup remote = mediaId -> {
            networkCalls[0]++;
            return VocaDbMetadataCacheTest.song(mediaId);
        };
        VocaDbRepository repository = new VocaDbRepository(remote, cache);

        assertEquals(42, repository.lookupByNicoVideoId("sm1").id());
        assertEquals(42, repository.lookupByNicoVideoId("sm1").id());
        assertEquals(1, networkCalls[0]);
    }

    @Test
    public void explicitNotFoundIsNegativeCachedWithoutMaskingNetworkErrors() throws Exception {
        VocaDbMetadataCache cache = cache();
        int[] networkCalls = {0};
        VocaDbLookup remote = mediaId -> {
            networkCalls[0]++;
            if (networkCalls[0] == 1) return null;
            throw new IOException("must not retry while negative cache is valid");
        };
        VocaDbRepository repository = new VocaDbRepository(remote, cache);

        assertNull(repository.lookupByNicoVideoId("sm404"));
        assertNull(repository.lookupByNicoVideoId("sm404"));
        assertEquals(1, networkCalls[0]);
    }

    @Test
    public void remoteResultForDifferentNicoIdIsRejectedAndNotCached() throws Exception {
        VocaDbMetadataCache cache = cache();
        VocaDbRepository repository = new VocaDbRepository(
                mediaId -> VocaDbMetadataCacheTest.song("sm2"),
                cache
        );

        IOException failure = assertThrows(
                IOException.class,
                () -> repository.lookupByNicoVideoId("sm1")
        );

        assertEquals("VocaDB result did not match requested media ID", failure.getMessage());
        assertNull(cache.get("sm1"));
        assertNull(cache.get("sm2"));
    }

    @Test
    public void cacheWriteFailureDoesNotHideUsableRemoteResult() throws Exception {
        File root = temporaryFolder.newFolder("cache-write-failure");
        MorpheCache raw = new MorpheCache(root, 4096, 16384, () -> 1000L);
        VocaDbMetadataCache cache = new VocaDbMetadataCache(raw, 1000L, 100L);
        assertTrueDelete(root);
        try (FileOutputStream output = new FileOutputStream(root)) {
            output.write(1);
        }
        VocaDbRepository repository = new VocaDbRepository(
                VocaDbMetadataCacheTest::song,
                cache
        );

        assertEquals(42, repository.lookupByNicoVideoId("sm1").id());
    }

    @Test
    public void remoteFailureIsNeverNegativeCached() throws Exception {
        VocaDbMetadataCache cache = cache();
        int[] networkCalls = {0};
        VocaDbRepository repository = new VocaDbRepository(mediaId -> {
            networkCalls[0]++;
            throw new IOException("offline");
        }, cache);

        assertThrows(IOException.class, () -> repository.lookupByNicoVideoId("sm1"));
        assertThrows(IOException.class, () -> repository.lookupByNicoVideoId("sm1"));
        assertEquals(2, networkCalls[0]);
        assertNull(cache.get("sm1"));
    }

    private VocaDbMetadataCache cache() throws Exception {
        MorpheCache raw = new MorpheCache(
                temporaryFolder.newFolder(),
                4096,
                16384,
                () -> 1000L
        );
        return new VocaDbMetadataCache(raw, 1000L, 100L);
    }

    private static void assertTrueDelete(File file) throws IOException {
        if (!file.delete()) {
            throw new IOException("Could not prepare cache write failure fixture");
        }
    }
}
