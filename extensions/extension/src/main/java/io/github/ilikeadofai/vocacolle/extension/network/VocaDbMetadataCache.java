package io.github.ilikeadofai.vocacolle.extension.network;

import io.github.ilikeadofai.vocacolle.extension.cache.MorpheCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Typed VocaDB metadata codec backed by the bounded atomic Morphe cache. */
public final class VocaDbMetadataCache {
    static final String NAMESPACE = "vocadb-song-v1";
    public static final long DEFAULT_POSITIVE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    public static final long DEFAULT_NOT_FOUND_TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final int PAYLOAD_VERSION = 1;
    private static final String KIND_SONG = "song";
    private static final String KIND_NOT_FOUND = "not_found";

    private final MorpheCache cache;
    private final long positiveTtlMillis;
    private final long notFoundTtlMillis;

    public VocaDbMetadataCache(MorpheCache cache) {
        this(cache, DEFAULT_POSITIVE_TTL_MILLIS, DEFAULT_NOT_FOUND_TTL_MILLIS);
    }

    VocaDbMetadataCache(MorpheCache cache, long positiveTtlMillis, long notFoundTtlMillis) {
        this.cache = Objects.requireNonNull(cache, "cache");
        if (positiveTtlMillis <= 0L || notFoundTtlMillis <= 0L) {
            throw new IllegalArgumentException("VocaDB cache TTLs must be positive");
        }
        this.positiveTtlMillis = positiveTtlMillis;
        this.notFoundTtlMillis = notFoundTtlMillis;
    }

    /** Returns {@code null} for a cache miss or a malformed typed payload. */
    public Lookup get(String mediaId) throws IOException {
        String normalizedMediaId = VocaDbIds.requireMediaId(mediaId);
        byte[] payload = cache.get(NAMESPACE, normalizedMediaId);
        if (payload == null) {
            return null;
        }
        try {
            return decode(payload, normalizedMediaId);
        } catch (JSONException | RuntimeException malformed) {
            cache.remove(NAMESPACE, normalizedMediaId);
            return null;
        }
    }

    public void putSong(VocaDbSong song) throws IOException {
        Objects.requireNonNull(song, "song");
        cache.put(
                NAMESPACE,
                VocaDbIds.requireMediaId(song.nicoVideoId()),
                encodeSong(song),
                positiveTtlMillis
        );
    }

    public void putNotFound(String mediaId) throws IOException {
        String normalizedMediaId = VocaDbIds.requireMediaId(mediaId);
        try {
            JSONObject object = basePayload(KIND_NOT_FOUND, normalizedMediaId);
            cache.put(
                    NAMESPACE,
                    normalizedMediaId,
                    object.toString().getBytes(StandardCharsets.UTF_8),
                    notFoundTtlMillis
            );
        } catch (JSONException impossible) {
            throw new IOException("Could not encode VocaDB not-found cache entry", impossible);
        }
    }

    private static byte[] encodeSong(VocaDbSong song) throws IOException {
        try {
            JSONObject object = basePayload(KIND_SONG, song.nicoVideoId());
            object.put("songId", song.id());
            object.put("defaultName", song.defaultName());
            JSONObject names = new JSONObject();
            for (Map.Entry<String, String> entry : song.names().entrySet()) {
                names.put(entry.getKey(), entry.getValue());
            }
            object.put("names", names);
            JSONArray youtubeIds = new JSONArray();
            for (String videoId : song.youtubeOriginalVideoIds()) {
                youtubeIds.put(videoId);
            }
            object.put("youtubeOriginalVideoIds", youtubeIds);
            return object.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException failure) {
            throw new IOException("Could not encode VocaDB song cache entry", failure);
        }
    }

    private static JSONObject basePayload(String kind, String mediaId) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("version", PAYLOAD_VERSION);
        object.put("kind", kind);
        object.put("mediaId", mediaId);
        return object;
    }

    private static Lookup decode(byte[] payload, String requestedMediaId) throws JSONException {
        JSONObject object = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        if (object.getInt("version") != PAYLOAD_VERSION
                || !requestedMediaId.equals(object.getString("mediaId"))) {
            throw new JSONException("Unexpected VocaDB cache identity");
        }
        String kind = object.getString("kind");
        if (KIND_NOT_FOUND.equals(kind)) {
            return Lookup.notFound();
        }
        if (!KIND_SONG.equals(kind)) {
            throw new JSONException("Unknown VocaDB cache entry kind");
        }

        Map<String, String> names = new LinkedHashMap<>();
        JSONObject encodedNames = object.getJSONObject("names");
        Iterator<String> keys = encodedNames.keys();
        while (keys.hasNext()) {
            String language = keys.next();
            names.put(language, encodedNames.getString(language));
        }
        List<String> youtubeIds = new ArrayList<>();
        JSONArray encodedYoutubeIds = object.getJSONArray("youtubeOriginalVideoIds");
        for (int index = 0; index < encodedYoutubeIds.length(); index++) {
            youtubeIds.add(encodedYoutubeIds.getString(index));
        }
        return Lookup.song(new VocaDbSong(
                object.getInt("songId"),
                object.getString("defaultName"),
                requestedMediaId,
                names,
                youtubeIds
        ));
    }

    public static final class Lookup {
        private final VocaDbSong song;
        private final boolean notFound;

        private Lookup(VocaDbSong song, boolean notFound) {
            this.song = song;
            this.notFound = notFound;
        }

        static Lookup song(VocaDbSong song) {
            return new Lookup(Objects.requireNonNull(song, "song"), false);
        }

        static Lookup notFound() {
            return new Lookup(null, true);
        }

        public VocaDbSong song() {
            return song;
        }

        public boolean isNotFound() {
            return notFound;
        }
    }
}
