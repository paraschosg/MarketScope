package com.marketscope.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Plain GET requests. MarketScope says who it is in the User-Agent and only reads public data;
 * it never signs in anywhere and never places orders.
 */
public final class Http {

    static final String USER_AGENT = "MarketScope/0.1 (personal desktop app)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    public static String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 429) {
                throw new IOException(request.uri().getHost() + " is asking us to slow down (HTTP 429). Try again"
                        + " in a minute.");
            }
            if (status < 200 || status >= 300) {
                throw new IOException(request.uri().getHost() + " answered HTTP " + status + ".");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + request.uri().getHost(), e);
        }
    }
}
