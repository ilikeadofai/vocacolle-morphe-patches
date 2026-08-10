package io.github.ilikeadofai.vocacolle.extension.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class VocaDbClientTest {
    @Test
    public void exactLookupReturnsNullOnlyForExplicitJsonNull() throws Exception {
        MorpheHttpClientTest.FakeConnection connection = jsonConnection("null");

        VocaDbSong song = client(connection).lookupByNicoVideoId("sm9");

        assertNull(song);
    }

    @Test
    public void exactLookupRejectsResponseWithoutRequestedNicoPv() throws Exception {
        MorpheHttpClientTest.FakeConnection connection = jsonConnection(
                "{\"id\":1,\"name\":\"Wrong\",\"names\":[],\"pvs\":["
                        + "{\"pvId\":\"sm999\",\"service\":\"NicoNicoDouga\","
                        + "\"pvType\":\"Original\",\"disabled\":false}]}"
        );

        IOException failure = assertThrows(
                IOException.class,
                () -> client(connection).lookupByNicoVideoId("sm15630734")
        );

        assertEquals(
                "VocaDB response did not contain the requested NicoNico PV",
                failure.getMessage()
        );
    }

    @Test
    public void exactLookupRejectsUnsafeMediaIdBeforeOpeningConnection() throws Exception {
        int[] opens = {0};
        MorpheHttpClient httpClient = new MorpheHttpClient(url -> {
            opens[0]++;
            return jsonConnection("null");
        }, 1000, 1000, 64 * 1024);

        assertThrows(
                IllegalArgumentException.class,
                () -> new VocaDbClient(httpClient).lookupByNicoVideoId("sm1&fields=Everything")
        );
        assertEquals(0, opens[0]);
    }

    @Test
    public void exactLookupRejectsHttpErrorsBeforeParsingBody() throws Exception {
        MorpheHttpClientTest.FakeConnection connection = jsonConnection("null");
        connection.status = 503;

        IOException failure = assertThrows(
                IOException.class,
                () -> client(connection).lookupByNicoVideoId("sm15630734")
        );

        assertEquals("VocaDB returned HTTP 503", failure.getMessage());
    }

    @Test
    public void exactLookupRejectsNonJsonSuccessResponses() throws Exception {
        MorpheHttpClientTest.FakeConnection connection =
                new MorpheHttpClientTest.FakeConnection(new URL("https://vocadb.net/placeholder"));
        connection.status = 200;
        connection.contentType = "text/html; charset=utf-8";
        connection.body = "<html>proxy error</html>".getBytes("UTF-8");
        MorpheHttpClient httpClient = new MorpheHttpClient(
                url -> connection,
                1000,
                1000,
                64 * 1024
        );

        IOException failure = assertThrows(
                IOException.class,
                () -> new VocaDbClient(httpClient).lookupByNicoVideoId("sm15630734")
        );
        assertEquals("VocaDB returned unsupported content type: text/html; charset=utf-8",
                failure.getMessage());
    }

    @Test
    public void exactNicoLookupReturnsVerifiedNamesAndOriginalPvs() throws Exception {
        MorpheHttpClientTest.FakeConnection connection =
                new MorpheHttpClientTest.FakeConnection(new URL("https://vocadb.net/placeholder"));
        connection.status = 200;
        connection.contentType = "application/json; charset=utf-8";
        connection.body = ("{"
                + "\"id\":8394,"
                + "\"name\":\"千本桜\","
                + "\"names\":["
                + "{\"language\":\"Japanese\",\"value\":\"千本桜\"},"
                + "{\"language\":\"English\",\"value\":\"A Thousand Cherry Blossoms\"}],"
                + "\"pvs\":["
                + "{\"pvId\":\"sm15630734\",\"service\":\"NicoNicoDouga\","
                + "\"pvType\":\"Original\",\"disabled\":false},"
                + "{\"pvId\":\"OriginalVideo42\",\"service\":\"Youtube\","
                + "\"pvType\":\"Original\",\"disabled\":false},"
                + "{\"pvId\":\"OriginalVideo42\",\"service\":\"Youtube\","
                + "\"pvType\":\"Original\",\"disabled\":false},"
                + "{\"pvId\":\"DisabledVideo\",\"service\":\"Youtube\","
                + "\"pvType\":\"Original\",\"disabled\":true},"
                + "{\"pvId\":\"ReprintVideo\",\"service\":\"Youtube\","
                + "\"pvType\":\"Reprint\",\"disabled\":false}]}")
                .getBytes("UTF-8");
        final URL[] requestedUrl = {null};
        MorpheHttpClient httpClient = new MorpheHttpClient(url -> {
            requestedUrl[0] = url;
            return connection;
        }, 1000, 1000, 64 * 1024);

        VocaDbSong song = new VocaDbClient(httpClient).lookupByNicoVideoId("sm15630734");

        assertEquals(8394, song.id());
        assertEquals("千本桜", song.defaultName());
        assertEquals("千本桜", song.name("Japanese"));
        assertEquals("A Thousand Cherry Blossoms", song.name("English"));
        assertEquals("sm15630734", song.nicoVideoId());
        assertEquals(Arrays.asList("OriginalVideo42"), song.youtubeOriginalVideoIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> song.youtubeOriginalVideoIds().add("Mutation")
        );
        assertEquals("https", requestedUrl[0].getProtocol());
        assertEquals("vocadb.net", requestedUrl[0].getHost());
        assertEquals("/api/songs/byPv", requestedUrl[0].getPath());
        assertEquals("NicoNicoDouga", query(requestedUrl[0]).get("pvService"));
        assertEquals("sm15630734", query(requestedUrl[0]).get("pvId"));
        assertEquals("Names,PVs,Artists", query(requestedUrl[0]).get("fields"));
    }

    private static Map<String, String> query(URL url) throws Exception {
        Map<String, String> result = new HashMap<>();
        for (String pair : url.getQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], "UTF-8"),
                    URLDecoder.decode(parts.length == 1 ? "" : parts[1], "UTF-8")
            );
        }
        return result;
    }

    private static VocaDbClient client(MorpheHttpClientTest.FakeConnection connection) {
        return new VocaDbClient(
                new MorpheHttpClient(url -> connection, 1000, 1000, 64 * 1024)
        );
    }

    private static MorpheHttpClientTest.FakeConnection jsonConnection(String body)
            throws IOException {
        MorpheHttpClientTest.FakeConnection connection =
                new MorpheHttpClientTest.FakeConnection(new URL("https://vocadb.net/placeholder"));
        connection.status = 200;
        connection.contentType = "application/json; charset=utf-8";
        connection.body = body.getBytes("UTF-8");
        return connection;
    }
}