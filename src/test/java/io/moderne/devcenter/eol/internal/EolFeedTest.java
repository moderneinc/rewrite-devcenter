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

import io.moderne.devcenter.eol.EolStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EolFeedTest {

    private final EolFeed bundled = FeedLoader.load(null);

    @Test
    void cycleMatchesVersionByExactValueOrDotPrefix() {
        assertThat(EolFeed.cycleMatchesVersion("6.1", "6.1")).isTrue();
        assertThat(EolFeed.cycleMatchesVersion("6.1", "6.1.14")).isTrue();
        assertThat(EolFeed.cycleMatchesVersion("2.7", "2.7.18.RELEASE")).isTrue();
        assertThat(EolFeed.cycleMatchesVersion("6.1", "6.10.0")).isFalse();
        assertThat(EolFeed.cycleMatchesVersion("6", "61.0")).isFalse();
    }

    @Test
    void matchesSpringBootToLongestCyclePrefixInBundledFeed() {
        // Bundled-feed sanity check: confirm the shipped feed still tracks Spring Boot and resolves
        // the longest dot-prefix cycle. The exact EOL date is intentionally NOT asserted here — it
        // changes when the feed is regenerated; date-sensitive assertions live in
        // DependencyEndOfLifeTest against the fixed test feed.
        Optional<EolFeed.Match> match = bundled.match(Purl.maven("org.springframework.boot"), "3.4.2");
        assertThat(match).hasValueSatisfying(m -> {
            assertThat(m.getProductSlug()).isEqualTo("spring-boot");
            assertThat(m.getCycle()).isEqualTo("3.4");
            assertThat(m.getEolFrom()).isNotNull();
        });
    }

    @Test
    void bundledFeedCarriesRuntimesAndOtherEcosystems() {
        assertThat(bundled.match(Purl.runtime("openjdk"), "8")).isPresent();
        assertThat(bundled.match(Purl.runtime("node"), "18")).isPresent();
        assertThat(bundled.match(Purl.npm("express"), "4.18.2")).isPresent();
    }

    @Test
    void noMatchForUntrackedGroup() {
        assertThat(bundled.match(Purl.maven("com.example.unknown"), "1.0.0")).isEmpty();
    }

    @Test
    void noMatchWhenVersionFallsInNoCycle() {
        assertThat(bundled.match(Purl.maven("org.springframework.boot"), "99.0.0")).isEmpty();
    }

    @Test
    void parsesInlineYamlFeed() {
        String yaml = "schemaVersion: 1\n" +
                      "products:\n" +
                      "  - name: acme\n" +
                      "    label: Acme\n" +
                      "    coordinates: [pkg:maven/com.acme]\n" +
                      "    cycles:\n" +
                      "      - { cycle: \"1\", eolDate: 2020-01-01, eol: true }\n";
        EolFeed feed = EolFeed.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(feed.match(Purl.maven("com.acme"), "1.2.3")).hasValueSatisfying(m ->
                assertThat(m.getEolFrom()).isEqualTo(LocalDate.parse("2020-01-01")));
    }

    @Test
    void parsesInlineJsonFeed() {
        String json = "{\"schemaVersion\":1,\"products\":[{\"name\":\"acme\",\"label\":\"Acme\"," +
                      "\"coordinates\":[\"pkg:npm/acme\"],\"cycles\":[{\"cycle\":\"2\",\"eol\":true}]}]}";
        EolFeed feed = EolFeed.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThat(feed.match(Purl.npm("acme"), "2.0.0")).hasValueSatisfying(m -> {
            assertThat(m.getEolFrom()).isNull();
            assertThat(m.isEol()).isTrue();
        });
    }

    @Test
    void statusFollowsEolDateRelativeToReference() {
        LocalDate asOf = LocalDate.parse("2026-05-28");
        assertThat(EolStatus.of(LocalDate.parse("2025-12-31"), false, asOf, 180)).isEqualTo(EolStatus.EndOfLife);
        assertThat(EolStatus.of(LocalDate.parse("2026-06-30"), false, asOf, 180)).isEqualTo(EolStatus.EndOfLifeApproaching);
        assertThat(EolStatus.of(LocalDate.parse("2026-12-31"), false, asOf, 180)).isEqualTo(EolStatus.Supported);
        assertThat(EolStatus.of(null, true, asOf, 180)).isEqualTo(EolStatus.EndOfLife);
        assertThat(EolStatus.of(null, false, asOf, 180)).isEqualTo(EolStatus.Supported);
    }
}
