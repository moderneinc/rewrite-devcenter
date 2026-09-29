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

import org.junit.jupiter.api.Test;
import org.openrewrite.SourceFile;
import org.openrewrite.json.JsonParser;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;

class JsonLineCounterTest {

    @Test
    void jsonMatchesSource() {
        assertParsedCountsMatchSource(JsonLineCounterTest::parse, Json.Document.class, JsonLineCounter::count, 1, 15,
          """
            {
              "name": "example",
              "version": "1.0.0",
              "escapes": "tab\\t newline\\n quote\\" unicode \\u00e9",
              "dependencies": {
                "a": "^1.2.3",
                "b": ["x", "y", {"nested": [1, 2.5, -3e10, true, false, null]}]
              },
              "empty": {},
              "emptyArray": [],
              "spaced": [
              ]
            }
            """,
          """
            // leading comment
            {
              /* block
                 comment */
              "a": 1, // trailing
              "b": [
                1,
                2
              ]
            }
            // ends in a comment""",
          """
            {
              unquoted: 'single',
              trailing: [1, 2,],
            }
            """,
          "\"just a string\"",
          "42\n",
          "[]",
          "{\"a\":1}/* end\n*/",
          nested(40)
        );
    }

    @Test
    void markersAreNotCounted() {
        Json.Document document = (Json.Document) parse("{\n  \"a\": [1,\n    2]\n}\n").orElseThrow();
        Json.Document marked = (Json.Document) new JsonVisitor<Integer>() {
            @Override
            public Json visitLiteral(Json.Literal literal, Integer p) {
                return SearchResult.found(literal, "multi\nline");
            }

            @Override
            public Json visitArray(Json.Array array, Integer p) {
                return Markup.warn(super.visitArray(array, p), new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(document, 0);

        assertThat(marked.printAll()).isNotEqualTo(document.printAll());
        assertThat(JsonLineCounter.count(marked)).isEqualTo(JsonLineCounter.count(document)).isEqualTo(4);
    }

    private static Optional<SourceFile> parse(String source) {
        return JsonParser.builder().build().parse(source).findFirst();
    }

    private static String nested(int depth) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            json.append("  ".repeat(i)).append(i % 2 == 0 ? "{\"k" + i + "\":\n" : "[\n");
        }
        json.append("  ".repeat(depth)).append("null\n");
        for (int i = depth - 1; i >= 0; i--) {
            json.append("  ".repeat(i)).append(i % 2 == 0 ? "}\n" : "]\n");
        }
        return json.toString();
    }
}
