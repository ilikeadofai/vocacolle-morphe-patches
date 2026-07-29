package io.github.ilikeadofai.vocacolle.extension.network;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class VocaDbTitleSelectorTest {
    @Test
    public void prefersEnglishThenRomajiThenOriginal() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("English", "World is Mine");
        names.put("Romaji", "Sekai de Ichiban Ohime-sama");
        VocaDbSong english = new VocaDbSong(
                1, "ワールドイズマイン", "sm1", names, Collections.emptyList());
        assertEquals("World is Mine", VocaDbTitleSelector.select(english, "原題"));

        names.remove("English");
        VocaDbSong romaji = new VocaDbSong(
                2, "ワールドイズマイン", "sm2", names, Collections.emptyList());
        assertEquals("Sekai de Ichiban Ohime-sama", VocaDbTitleSelector.select(romaji, "原題"));

        VocaDbSong originalOnly = new VocaDbSong(
                3, "原題", "sm3", Collections.emptyMap(), Collections.emptyList());
        assertEquals("原題", VocaDbTitleSelector.select(originalOnly, "原題"));
        assertEquals("原題", VocaDbTitleSelector.select(null, "原題"));
    }
}
