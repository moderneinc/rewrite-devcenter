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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.toml.TomlIsoVisitor;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Toml;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static io.moderne.devcenter.internal.LineCountAssertions.sanitizedPrint;
import static org.assertj.core.api.Assertions.assertThat;

class TomlLineCounterTest {

    @Test
    void countsMatchSource() {
        assertParsedCountsMatchSource(TomlLineCounterTest::parse, Toml.Document.class, TomlLineCounter::count, 11, 15,
          """
            # Cargo manifest
            [package]
            name = "demo" # trailing comment
            version = "0.1.0"
            edition = "2021"

            [dependencies]
            serde = { version = "1.0", features = ["derive"] }
            tokio = { version = "1", features = [
                "full",
                "macros", # inline comment
            ] }

            [dev-dependencies.criterion]
            version = "0.5"
            """,
          """
            basic = \"""
            first line
              second line \\
              continued
            \"""
            literal = '''
            C:\\raw\\path
            '''
            one-line = \"""single\"""
            escaped = "tab\\tquote\\"end"
            """,
          """
            [[products]]
            name = "Hammer"
            sku = 738594937

            [[products]]  # empty table

            [[products]]
            name = "Nail"
            color = "gray"
            """,
          """
            a.b.c = 1
            "quoted key" = true
            'literal key' = 2024-05-27T07:32:00Z
            site."google.com" = true
            float = +1.0e-3
            ints = [ 1, 2, 3, ]
            nested = [ [ 1, 2 ], [ "a",
              "b" ], [ { x = 1 }, { y = [ 2,
                3 ] } ] ]
            empty = []
            inline = {}
            """,
          """
            [a]
            [a.b]
            [a.b.c]
            d = { e = { f = { g = [ { h = 1 } ] } } }
            # ends with a comment""",
          """
            # only comments

            # and blank lines
            """,
          "key = \"value\"",
          "[table]"
        );
    }

    @Test
    void markersAreNotCounted() {
        Toml.Document toml = parse("[a]\nb = 1\nc = \"d\"\n").map(Toml.Document.class::cast).orElseThrow();
        Toml.Document marked = (Toml.Document) new TomlIsoVisitor<ExecutionContext>() {
            @Override
            public Toml.Literal visitLiteral(Toml.Literal literal, ExecutionContext ctx) {
                return "1".equals(literal.getSource()) ? SearchResult.found(literal, "multi\nline") :
                  Markup.warn(literal, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(toml, new InMemoryExecutionContext());
        assertThat(marked.printAll()).isNotEqualTo(sanitizedPrint(marked));
        assertThat(TomlLineCounter.count(marked)).isEqualTo(TomlLineCounter.count(toml)).isEqualTo(3);
    }

    private static Optional<SourceFile> parse(String source) {
        return TomlParser.builder().build().parse(source).findFirst();
    }
}
