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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;

class XmlLineCounterTest {

    @Test
    void xmlMatchesSource() {
        assertParsedCountsMatchSource(parser("file.xml"), Xml.Document.class, XmlLineCounter::count, 1, 15,
          """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- license
                 header -->
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <modelVersion>4.0.0</modelVersion>
                <dependencies>
                    <dependency>
                        <groupId>a</groupId>
                    </dependency>
                </dependencies>
            </project>
            """,
          """
            <?xml version="1.0"?>
            <!DOCTYPE note [
              <!ELEMENT note (to,from)>
              <!ELEMENT to (#PCDATA)>
              <!ENTITY writer "Writer: Donald Duck.">
            ]>
            <note>
              <to>&writer;</to>
            </note>
            """,
          """
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
              "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html/>
            """,
          """
            <root>
              <?pi some
                 data?>
              <script><![CDATA[
                if (a < b) {
                }
              ]]></script>
              <empty/>
              <a key='single' other="multi
            line" >
              text &amp; more
              text</a>
            </root>
            <!-- trailing
                 comment -->
            """,
          "<a/>",
          "<a></a><!-- end -->",
          nested(40)
        );
    }

    @Test
    void htmlAndJspMatchSource() {
        assertParsedCountsMatchSource(parser("page.jsp"), Xml.Document.class, XmlLineCounter::count, 2, 15,
          """
            <%@ page language="java"
                contentType="text/html" %>
            <html>
            <body>
              <br>
              <% int a = 1;
                 a++; %>
              <%= a
              %>
              <%-- multi
                line --%>
              <%! int b; %>
            </body>
            </html>
            """
        );
    }

    @Test
    void markersAreNotCounted() {
        Xml.Document document = (Xml.Document) parser("file.xml").apply("<a>\n  <b>text</b>\n  <!-- c -->\n</a>\n").orElseThrow();
        Xml.Document marked = (Xml.Document) new XmlVisitor<Integer>() {
            @Override
            public Xml visitTag(Xml.Tag tag, Integer p) {
                return SearchResult.found(super.visitTag(tag, p), "multi\nline");
            }

            @Override
            public Xml visitComment(Xml.Comment comment, Integer p) {
                return Markup.warn(comment, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(document, 0);

        assertThat(marked.printAll()).isNotEqualTo(document.printAll());
        assertThat(XmlLineCounter.count(marked)).isEqualTo(XmlLineCounter.count(document)).isEqualTo(4);
    }

    private static Function<String, Optional<SourceFile>> parser(String path) {
        return source -> XmlParser.builder().build()
          .parseInputs(List.of(Parser.Input.fromString(Path.of(path), source)), null, new InMemoryExecutionContext())
          .findFirst();
    }

    private static String nested(int depth) {
        StringBuilder xml = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            xml.append("  ".repeat(i)).append("<level").append(i).append(" n=\"").append(i).append("\">\n");
        }
        for (int i = depth - 1; i >= 0; i--) {
            xml.append("  ".repeat(i)).append("</level").append(i).append(">\n");
        }
        return xml.toString();
    }
}
