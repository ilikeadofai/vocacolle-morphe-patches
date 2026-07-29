package io.github.ilikeadofai.vocacolle.extension.network;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Exact-match VocaDB metadata client for NicoNico video IDs. */
public final class VocaDbClient implements VocaDbLookup {
    private static final String ENDPOINT = "https://vocadb.net/api/songs/byPv";
    private static final String FIELDS = "Names,PVs,Artists";
    private static final String NICO_SERVICE = "NicoNicoDouga";
    private static final String YOUTUBE_SERVICE = "Youtube";
    private static final String ORIGINAL_PV_TYPE = "Original";

    private final MorpheHttpClient httpClient;

    public VocaDbClient(MorpheHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /** Returns {@code null} only when VocaDB explicitly returns JSON {@code null}. */
    @Override
    public VocaDbSong lookupByNicoVideoId(String mediaId) throws IOException {
        String normalizedMediaId = VocaDbIds.requireMediaId(mediaId);
        MorpheHttpClient.Response response = httpClient.get(buildLookupUrl(normalizedMediaId));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("VocaDB returned HTTP " + response.statusCode());
        }
        if (!isSupportedContentType(response.contentType())) {
            throw new IOException(
                    "VocaDB returned unsupported content type: " + response.contentType()
            );
        }
        String json = new String(response.body(), StandardCharsets.UTF_8).trim();
        if ("null".equals(json)) {
            return null;
        }
        if (json.isEmpty()) {
            throw new IOException("VocaDB returned an empty response");
        }
        try {
            return parseExactSong(new JSONObject(json), normalizedMediaId);
        } catch (JSONException | IllegalArgumentException failure) {
            throw new IOException("Invalid VocaDB song response", failure);
        }
    }

    private static URL buildLookupUrl(String mediaId) throws IOException {
        String query = "pvService=" + encode(NICO_SERVICE)
                + "&pvId=" + encode(mediaId)
                + "&fields=" + encode(FIELDS);
        return new URL(ENDPOINT + "?" + query);
    }

    private static VocaDbSong parseExactSong(JSONObject object, String requestedMediaId)
            throws JSONException, IOException {
        int id = object.getInt("id");
        String defaultName = object.getString("name");
        JSONArray pvs = object.getJSONArray("pvs");
        boolean exactNicoPvFound = false;
        LinkedHashSet<String> youtubeOriginalIds = new LinkedHashSet<>();
        for (int index = 0; index < pvs.length(); index++) {
            JSONObject pv = pvs.getJSONObject(index);
            String pvId = pv.optString("pvId", "");
            String service = pv.optString("service", "");
            String pvType = pv.optString("pvType", "");
            boolean disabled = pv.optBoolean("disabled", false);
            if (NICO_SERVICE.equals(service) && requestedMediaId.equals(pvId) && !disabled) {
                exactNicoPvFound = true;
            }
            if (YOUTUBE_SERVICE.equals(service)
                    && ORIGINAL_PV_TYPE.equals(pvType)
                    && !disabled
                    && !pvId.trim().isEmpty()) {
                youtubeOriginalIds.add(pvId);
            }
        }
        if (!exactNicoPvFound) {
            throw new IOException("VocaDB response did not contain the requested NicoNico PV");
        }

        Map<String, String> names = new LinkedHashMap<>();
        JSONArray nameEntries = object.optJSONArray("names");
        if (nameEntries != null) {
            for (int index = 0; index < nameEntries.length(); index++) {
                JSONObject name = nameEntries.getJSONObject(index);
                String language = name.optString("language", "").trim();
                String value = name.optString("value", "").trim();
                if (!language.isEmpty() && !value.isEmpty() && !names.containsKey(language)) {
                    names.put(language, value);
                }
            }
        }
        return new VocaDbSong(
                id,
                defaultName,
                requestedMediaId,
                names,
                new ArrayList<>(youtubeOriginalIds)
        );
    }


    private static boolean isSupportedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/json".equals(mediaType)
                || "text/json".equals(mediaType)
                || "text/plain".equals(mediaType);
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }
}
