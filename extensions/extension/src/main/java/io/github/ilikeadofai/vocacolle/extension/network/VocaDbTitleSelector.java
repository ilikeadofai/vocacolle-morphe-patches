package io.github.ilikeadofai.vocacolle.extension.network;

/** Display-only title selection for an exact VocaDB match. */
public final class VocaDbTitleSelector {
    private VocaDbTitleSelector() { }

    public static String select(VocaDbSong song, String originalTitle) {
        if (originalTitle == null) {
            return null;
        }
        if (song == null) {
            return originalTitle;
        }
        String english = usable(song.name("English"));
        if (english != null) {
            return english;
        }
        String romaji = usable(song.name("Romaji"));
        return romaji != null ? romaji : originalTitle;
    }

    private static String usable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }
}
