/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.devcenter.eol.internal;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@code feed} recipe option to a parsed {@link EolFeed}, so an organization can
 * supply its own end-of-life schedules without rebuilding the recipe.
 * <p>
 * Resolution order for the option value:
 * <ol>
 *   <li>{@code null}/blank &rarr; the bundled snapshot at {@code /eol/eol-feed.yaml}.</li>
 *   <li>{@code http://}/{@code https://} &rarr; fetched over HTTP.</li>
 *   <li>an existing file path &rarr; read from disk.</li>
 *   <li>otherwise &rarr; loaded as a classpath resource.</li>
 * </ol>
 * Results are cached per source so a feed is parsed (and any URL fetched) at most once per cache
 * window across all visited source files. Immutable sources (the bundled feed and other classpath
 * resources) are cached for the life of the JVM; mutable sources (files and URLs) are cached with a
 * TTL (default 1 hour, override with the {@code eol.feedTtlMillis} system property) so a long-lived
 * worker eventually picks up a refreshed feed. An org-supplied feed that fails to load does
 * <em>not</em> fail the run: a warning is logged and the bundled feed is used instead. For
 * reproducibility, prefer bundling/pre-fetching the feed over fetching a URL at recipe runtime.
 */
public final class FeedLoader {

    static final String BUNDLED_RESOURCE = "/eol/eol-feed.yaml";

    private static final Logger logger = LoggerFactory.getLogger(FeedLoader.class);
    private static final long DEFAULT_TTL_MILLIS = 60 * 60 * 1000L;
    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private FeedLoader() {
    }

    public static EolFeed load(@Nullable String feed) {
        String key = feed == null || feed.trim().isEmpty() ? BUNDLED_RESOURCE : feed.trim();
        long now = System.currentTimeMillis();
        CacheEntry cached = CACHE.get(key);
        if (cached != null && now < cached.expiresAt) {
            return cached.feed;
        }
        EolFeed feedValue = loadWithFallback(key);
        long expiresAt = isMutable(key) ? now + ttlMillis() : Long.MAX_VALUE;
        CACHE.put(key, new CacheEntry(feedValue, expiresAt));
        return feedValue;
    }

    /**
     * Load the source, falling back to the bundled feed (with a warning) if an org-supplied source
     * cannot be loaded — a bad path/URL must not fail the whole run. A failure to load the bundled
     * feed itself is a packaging error and is allowed to propagate.
     */
    private static EolFeed loadWithFallback(String key) {
        if (BUNDLED_RESOURCE.equals(key)) {
            return loadResource(BUNDLED_RESOURCE);
        }
        try {
            return loadUncached(key);
        } catch (RuntimeException e) {
            logger.warn("Failed to load EOL feed '{}'; falling back to the bundled feed", key, e);
            return loadResource(BUNDLED_RESOURCE);
        }
    }

    private static boolean isMutable(String key) {
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return true;
        }
        return Files.isRegularFile(Paths.get(key));
    }

    private static long ttlMillis() {
        try {
            String override = System.getProperty("eol.feedTtlMillis");
            return override == null ? DEFAULT_TTL_MILLIS : Long.parseLong(override);
        } catch (NumberFormatException e) {
            return DEFAULT_TTL_MILLIS;
        }
    }

    private static EolFeed loadUncached(String source) {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return fetch(source);
        }
        Path path = Paths.get(source);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                return EolFeed.parse(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read EOL feed file: " + source, e);
            }
        }
        return loadResource(source);
    }

    private static EolFeed loadResource(String resource) {
        String normalized = resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream in = FeedLoader.class.getResourceAsStream(normalized)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "EOL feed not found as a file, URL, or classpath resource: " + resource);
            }
            return EolFeed.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read EOL feed resource: " + resource, e);
        }
    }

    private static EolFeed fetch(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Unexpected status " + status + " fetching EOL feed " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                return EolFeed.parse(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch EOL feed: " + url, e);
        }
    }

    private static final class CacheEntry {
        final EolFeed feed;
        final long expiresAt;

        CacheEntry(EolFeed feed, long expiresAt) {
            this.feed = feed;
            this.expiresAt = expiresAt;
        }
    }
}
