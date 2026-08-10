package io.github.ilikeadofai.vocacolle.extension.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact VocaDB song metadata matched from a NicoNico video ID. */
public final class VocaDbSong {
    private final int id;
    private final String defaultName;
    private final String nicoVideoId;
    private final Map<String, String> names;
    private final List<String> youtubeOriginalVideoIds;

    VocaDbSong(
            int id,
            String defaultName,
            String nicoVideoId,
            Map<String, String> names,
            List<String> youtubeOriginalVideoIds
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        this.id = id;
        this.defaultName = requireText("defaultName", defaultName);
        this.nicoVideoId = requireText("nicoVideoId", nicoVideoId);
        this.names = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(names, "names")
        ));
        this.youtubeOriginalVideoIds = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(youtubeOriginalVideoIds, "youtubeOriginalVideoIds")
        ));
    }

    public int id() {
        return id;
    }

    public String defaultName() {
        return defaultName;
    }

    public String nicoVideoId() {
        return nicoVideoId;
    }

    public String name(String language) {
        return names.get(language);
    }

    public Map<String, String> names() {
        return names;
    }

    public List<String> youtubeOriginalVideoIds() {
        return youtubeOriginalVideoIds;
    }

    private static String requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
