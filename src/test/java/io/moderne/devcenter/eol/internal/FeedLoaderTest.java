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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FeedLoaderTest {

    @Test
    void nullFeedLoadsBundledSnapshot() {
        EolFeed feed = FeedLoader.load(null);
        assertThat(feed.match(Purl.maven("org.springframework.boot"), "3.4.0")).isPresent();
    }

    @Test
    void blankFeedAlsoLoadsBundledSnapshot() {
        assertThat(FeedLoader.load("  ").match(Purl.maven("org.springframework.boot"), "3.4.0")).isPresent();
    }

    @Test
    void classpathResourceOverridesBundled() {
        EolFeed feed = FeedLoader.load("/feeds/test-feed.yaml");
        // The internal product exists in the test feed but not in the bundled snapshot.
        assertThat(feed.match(Purl.maven("com.acme.platform"), "1.4.0")).isPresent();
        assertThat(FeedLoader.load(null).match(Purl.maven("com.acme.platform"), "1.4.0")).isEmpty();
    }

    @Test
    void filePathIsLoaded(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("my-feed.yaml");
        Files.write(file, ("schemaVersion: 1\nproducts:\n  - name: acme\n    coordinates: [pkg:maven/com.acme]\n" +
                           "    cycles:\n      - { cycle: \"1\", eol: true }\n").getBytes(StandardCharsets.UTF_8));
        assertThat(FeedLoader.load(file.toString()).match(Purl.maven("com.acme"), "1.0.0")).isPresent();
    }

    @Test
    void unknownSourceFallsBackToBundled() {
        // A bad org-supplied feed must not fail the run; it falls back to the bundled feed.
        EolFeed feed = FeedLoader.load("/feeds/does-not-exist.yaml");
        assertThat(feed.match(Purl.maven("org.springframework.boot"), "3.4.0")).isPresent();
    }

    @Test
    void malformedFeedFileFallsBackToBundled(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken-feed.yaml");
        Files.write(file, "this: [is, not, a, valid, feed".getBytes(StandardCharsets.UTF_8));
        EolFeed feed = FeedLoader.load(file.toString());
        assertThat(feed.match(Purl.maven("org.springframework.boot"), "3.4.0")).isPresent();
    }
}
