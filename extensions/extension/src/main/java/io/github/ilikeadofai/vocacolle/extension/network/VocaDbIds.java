package io.github.ilikeadofai.vocacolle.extension.network;

import java.util.Objects;

final class VocaDbIds {
    private VocaDbIds() { }

    static String requireMediaId(String mediaId) {
        Objects.requireNonNull(mediaId, "mediaId");
        String value = mediaId.trim();
        if (value.isEmpty() || value.length() > 128 || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("mediaId must be a safe NicoNico identifier");
        }
        return value;
    }
}
