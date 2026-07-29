package io.github.ilikeadofai.vocacolle.extension.network;

import java.io.IOException;
import java.util.Objects;

/** Cache-first VocaDB lookup that preserves remote errors and tolerates cache I/O failures. */
public final class VocaDbRepository implements VocaDbLookup {
    private final VocaDbLookup remote;
    private final VocaDbMetadataCache cache;

    public VocaDbRepository(VocaDbLookup remote, VocaDbMetadataCache cache) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public VocaDbSong lookupByNicoVideoId(String mediaId) throws IOException {
        String normalizedMediaId = VocaDbIds.requireMediaId(mediaId);
        VocaDbMetadataCache.Lookup cached = null;
        try {
            cached = cache.get(normalizedMediaId);
        } catch (IOException cacheFailure) {
            // Cache failure must not prevent a fresh exact metadata lookup.
        }
        if (cached != null) {
            return cached.isNotFound() ? null : cached.song();
        }

        VocaDbSong remoteSong = remote.lookupByNicoVideoId(normalizedMediaId);
        if (remoteSong != null && !normalizedMediaId.equals(remoteSong.nicoVideoId())) {
            throw new IOException("VocaDB result did not match requested media ID");
        }
        try {
            if (remoteSong == null) {
                cache.putNotFound(normalizedMediaId);
            } else {
                cache.putSong(remoteSong);
            }
        } catch (IOException | IllegalArgumentException cacheFailure) {
            // A usable remote result wins over cache persistence.
        }
        return remoteSong;
    }
}
