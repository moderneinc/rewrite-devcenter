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
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;

class PropertiesLineCounterTest {

    @Test
    void propertiesMatchSource() {
        assertParsedCountsMatchSource(PropertiesLineCounterTest::parse, Properties.File.class, PropertiesLineCounter::count, 1, 15,
          """
            # comment
            ! bang comment
            key=value
            key2 : value2
            key3 value3
            empty=
            spaced  =  x
            multi=first \\
              second \\
              third
            multi\\
            key=value
            unicode=\\u00e9
            url=http://host:8080/a=b
            emptykey
            """,
          "\n\n  indented=value\n\t\n",
          "a=b\n# ends in a comment",
          "# only a comment\n",
          "trailing=continuation \\\n",
          "=no key\n:colon\n"
        );
    }

    @Test
    void markersAreNotCounted() {
        Properties.File file = (Properties.File) parse("# c\na=b\nc=d \\\n  e\n").orElseThrow();
        Properties.File marked = (Properties.File) new PropertiesVisitor<Integer>() {
            @Override
            public Properties visitEntry(Properties.Entry entry, Integer p) {
                return SearchResult.found(entry, "multi\nline");
            }

            @Override
            public Properties visitComment(Properties.Comment comment, Integer p) {
                return Markup.warn(comment, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(file, 0);

        assertThat(marked.printAll()).isNotEqualTo(file.printAll());
        assertThat(PropertiesLineCounter.count(marked)).isEqualTo(PropertiesLineCounter.count(file)).isEqualTo(4);
    }

    private static Optional<SourceFile> parse(String source) {
        return PropertiesParser.builder().build().parse(source).findFirst();
    }
}
