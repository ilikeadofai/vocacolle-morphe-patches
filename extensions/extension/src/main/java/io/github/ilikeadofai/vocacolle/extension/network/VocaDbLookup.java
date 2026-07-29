package io.github.ilikeadofai.vocacolle.extension.network;

import java.io.IOException;

/** Source of exact VocaDB metadata for one NicoNico media ID. */
@FunctionalInterface
public interface VocaDbLookup {
    VocaDbSong lookupByNicoVideoId(String mediaId) throws IOException;
}
