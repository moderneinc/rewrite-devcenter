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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed, indexed <em>Moderne EOL feed</em>: a vendor-neutral, package-URL-keyed description of
 * products and their release-cycle end-of-life dates (see {@code FEED-FORMAT.md}). The feed is
 * canonically YAML, but JSON is accepted too (YAML is a superset).
 * <p>
 * Products are indexed by the {@link Purl#key() purl key} of each of their coordinates, so a
 * detected artifact or runtime is matched by reducing it to a {@link Purl} and looking it up. The
 * matched release cycle is the one whose name is the longest dot-boundary prefix of the resolved
 * version — the same rule the original endoflife.date-only dataset used.
 */
public final class EolFeed {

    private static final YAMLMapper YAML = new YAMLMapper();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, Product> byPurlKey;

    private EolFeed(Map<String, Product> byPurlKey) {
        this.byPurlKey = byPurlKey;
    }

    /**
     * Parse a feed from a stream. The content may be YAML or JSON; it is sniffed by its first
     * non-whitespace character.
     */
    public static EolFeed parse(InputStream in) {
        try {
            byte[] bytes = readAll(in);
            JsonNode root = mapperFor(bytes).readTree(bytes);
            Map<String, Product> byPurlKey = new HashMap<>();
            for (JsonNode p : root.path("products")) {
                List<Cycle> cycles = new ArrayList<>();
                for (JsonNode c : p.path("cycles")) {
                    JsonNode eol = c.path("eolDate");
                    LocalDate eolDate = eol.isTextual() && !eol.asText().isEmpty() ?
                            LocalDate.parse(eol.asText()) : null;
                    cycles.add(new Cycle(c.path("cycle").asText(), eolDate, c.path("eol").asBoolean(false)));
                }
                Product product = new Product(p.path("name").asText(), p.path("label").asText(), cycles);
                for (JsonNode coordinate : p.path("coordinates")) {
                    // First coordinate wins on the (rare) chance two products share a purl key.
                    byPurlKey.putIfAbsent(Purl.parse(coordinate.asText()).key(), product);
                }
            }
            return new EolFeed(byPurlKey);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Moderne EOL feed", e);
        }
    }

    /**
     * Match a detected artifact or runtime to its product and release cycle.
     *
     * @return the match, or empty when the purl is not tracked or the version falls in no known cycle.
     */
    public Optional<Match> match(Purl purl, String version) {
        Product product = byPurlKey.get(purl.key());
        if (product == null) {
            return Optional.empty();
        }
        Cycle cycle = product.resolveCycle(version);
        if (cycle == null) {
            return Optional.empty();
        }
        return Optional.of(new Match(product.name, product.label, cycle.cycle, cycle.eolDate, cycle.eol));
    }

    static boolean cycleMatchesVersion(String cycle, String version) {
        return version.equals(cycle) || version.startsWith(cycle + ".");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static ObjectMapper mapperFor(byte[] bytes) {
        for (byte b : bytes) {
            if (Character.isWhitespace(b)) {
                continue;
            }
            return b == '{' || b == '[' ? JSON : YAML;
        }
        return YAML;
    }

    @Value
    public static class Match {
        String productSlug;
        String productLabel;
        String cycle;
        @Nullable
        LocalDate eolFrom;
        boolean isEol;
    }

    @Value
    private static class Product {
        String name;
        String label;
        List<Cycle> cycles;

        @Nullable
        Cycle resolveCycle(String version) {
            Cycle best = null;
            for (Cycle c : cycles) {
                if (cycleMatchesVersion(c.cycle, version) &&
                    (best == null || c.cycle.length() > best.cycle.length())) {
                    best = c;
                }
            }
            return best;
        }
    }

    @Value
    private static class Cycle {
        String cycle;
        @Nullable
        LocalDate eolDate;
        boolean eol;
    }
}
