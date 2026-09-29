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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static io.moderne.devcenter.internal.LineCountAssertions.sanitizedPrint;
import static org.assertj.core.api.Assertions.assertThat;

class YamlLineCounterTest {

    @Test
    void countsMatchSource() {
        assertParsedCountsMatchSource(YamlLineCounterTest::parse, Yaml.Documents.class, YamlLineCounter::count, 7, 15,
          """
            # Spring Boot configuration
            spring:
              application:
                name: demo # trailing comment
              datasource:
                url: jdbc:postgresql://localhost:5432/demo
                username: ${DB_USER:demo}

              jpa:
                hibernate.ddl-auto: validate
            server:
              port: 8080
            logging.level:
              root: INFO
              org.springframework: DEBUG
            """,
          """
            literal: |
              line one
                indented
              line three
            kept: |+
              keep trailing

            stripped: |-
              no trailing newline
            folded: >
              folded
              text

            folded-stripped: >-
              a
              b
            indented: |2
                two extra spaces
            last: |
              at the end
            """,
          """
            %YAML 1.2
            ---
            only: document
            ...
            """,
          """
            first: document
            ...
            ---
            second: document
            --- # empty third document
            ---
            - fourth
            ...
            """,
          """
            defaults: &defaults
              adapter: postgres
              host: localhost
            development:
              <<: *defaults
              database: dev
            tagged: !!str 123
            local: !thing value
            explicit: !<tag:yaml.org,2002:str> value
            anchored-list: &list
              - a
              - b
            reused: *list
            """,
          """
            flow-seq: [ one, two,
              three, "four",
              ]
            flow-map: { a: 1,
                        b: [ x, y ],
                        c: { d: e }, }
            nested: [[1, 2], [3, [4, 5]], {k: v}]
            """,
          """
            double: "multi
              line \\
              quoted"
            single: 'it''s
              also
              multi-line'
            plain: plain scalar
              continued on the next line
            empty:
            "quoted key": value
            ? complex key
            : complex value
            """,
          """
            a:
              b:
                c:
                  d:
                    - e:
                        f:
                          - - g
                            - h
                          - i: j
            # comment at the end""",
          """
            # only comments
            # and nothing else
            """,
          """
            - item
            -   - nested
                - list
            - key: value
              other: value
            -
            """,
          "key: value",
          "---\n",
          "plain"
        );
    }

    @Test
    void markersAreNotCounted() {
        Yaml.Documents yaml = parse("a:\n  b: c\nd: e\n").map(Yaml.Documents.class::cast).orElseThrow();
        Yaml.Documents marked = (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                return "c".equals(scalar.getValue()) ? SearchResult.found(scalar, "multi\nline") :
                  Markup.warn(scalar, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(yaml, new InMemoryExecutionContext());
        assertThat(marked.printAll()).isNotEqualTo(sanitizedPrint(marked));
        assertThat(YamlLineCounter.count(marked)).isEqualTo(YamlLineCounter.count(yaml)).isEqualTo(3);
    }

    private static Optional<SourceFile> parse(String source) {
        return YamlParser.builder().build().parse(source).findFirst();
    }
}
