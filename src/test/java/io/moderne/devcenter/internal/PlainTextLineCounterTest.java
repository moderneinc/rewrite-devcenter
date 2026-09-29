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
package io.moderne.devcenter.internal;

import io.moderne.devcenter.internal.LineCountAssertions.MultilineMarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.XmlParser;

import java.nio.file.Path;
import java.util.List;

import static io.moderne.devcenter.internal.LineCountAssertions.assertCountMatchesSource;
import static io.moderne.devcenter.internal.LineCountAssertions.assertMarkersNotCounted;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class PlainTextLineCounterTest {

    @Test
    void plainTextMatchesSource() {
        assertParsedCountsMatchSource(
          s -> PlainTextParser.builder().build().parse(s).findFirst(),
          PlainText.class, PlainTextLineCounter::count, 1, 25,
          "first\nsecond\n",
          "no trailing newline",
          "\r\nwindows\r\nlines\r\n",
          "\n\n\n",
          "  indented\n\ttabbed\n",
          "unicode ✓ 😀\n"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "\n", "\n\n", "a\n", "a\nb", "a\nb\n", "a\n\n\n", "\r", "a\rb", "\r\n", "a\r\nb", "a\r\nb\r\n"})
    void textMatchesSource(String text) {
        assertCountMatchesSource(plainText(text), PlainTextLineCounter::count);
    }

    @Test
    void snippetsMatchSource() {
        for (List<String> snippets : List.of(
          List.of("second"),
          List.of("second\n"),
          List.of("", "second"),
          List.of("second\n", ""),
          List.of("\n", "third\r\n"),
          List.of("", ""))) {
            List<PlainText.Snippet> s = snippets.stream()
              .map(text -> new PlainText.Snippet(randomId(), Markers.EMPTY, text))
              .toList();
            assertCountMatchesSource(plainText("first\n").withSnippets(s), PlainTextLineCounter::count);
            assertCountMatchesSource(plainText("first").withSnippets(s), PlainTextLineCounter::count);
            assertCountMatchesSource(plainText("").withSnippets(s), PlainTextLineCounter::count);
        }
    }

    @Test
    void markersAreNotCounted() {
        PlainText text = plainText("first\nsecond");
        assertMarkersNotCounted(text, SearchResult.found(text, "multi\nline"));
        assertMarkersNotCounted(text, Markup.warn(text, new IllegalStateException("one\ntwo")));
        assertMarkersNotCounted(text, text.withMarkers(text.getMarkers().add(new MultilineMarker(randomId()))));

        PlainText withSnippet = text.withSnippets(List.of(new PlainText.Snippet(randomId(), Markers.EMPTY, "third")));
        assertMarkersNotCounted(withSnippet, withSnippet.withSnippets(ListUtils.map(withSnippet.getSnippets(),
          s -> SearchResult.found(s, "multi\nline"))));
    }

    @Test
    void parseErrorCountsItsText() {
        for (String source : new String[]{"<a>\n  <b>\n", "<a>\r\n<b", "<", "\n<a\n\n"}) {
            SourceFile parsed = XmlParser.builder().build().parse(source).findFirst().orElseThrow();
            assertThat(parsed).isInstanceOf(ParseError.class);
            assertCountMatchesSource((ParseError) parsed, e -> Newlines.lineCount(e.getText()));
        }
    }

    private static PlainText plainText(String text) {
        return PlainText.builder().sourcePath(Path.of("file.txt")).text(text).build();
    }
}
