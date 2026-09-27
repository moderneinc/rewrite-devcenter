/*
 * Copyright 2025 the original author or authors.
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
package io.moderne.devcenter;

import io.moderne.devcenter.internal.LineCounters;
import io.moderne.devcenter.table.OrganizationStatistics;
import org.junit.jupiter.api.Test;
import org.openrewrite.golang.rpc.GoRewriteRpc;
import org.openrewrite.golang.tree.GoMod;
import org.openrewrite.golang.tree.GoSum;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

class FindOrganizationStatisticsTest implements RewriteTest {

    @Test
    void countsLinesOfCode() {
        assertThat(lineCount(java(
          """
            class Test {
                void method() {}
            }
            """
        ))).isEqualTo(3);
    }

    @Test
    void countsNewlinesInsideTextBlocks() {
        assertThat(lineCount(java(
          """
            class Test {
                String text = ""\"
                    line one
                    line two
                    ""\";
            }
            """
        ))).isEqualTo(6);
    }

    @Test
    void countsNewlinesInsideJavadoc() {
        assertThat(lineCount(java(
          """
            class Test {
                /**
                 * One.
                 * Two.
                 */
                void method() {
                }
            }
            """
        ))).isEqualTo(8);
    }

    @Test
    void countsFinalLineWithoutTrailingNewline() {
        assertThat(lineCount(java("class A {\n    int x = 1;\n}"))).isEqualTo(3);
    }

    @Test
    void countsNonJavaSourceFiles() {
        assertThat(lineCount(text("first\nsecond\nthird\n"))).isEqualTo(3);
    }

    @Test
    void countsGoModWithoutStartingGoEngine() {
        var goMod = new GoMod(randomId(), Space.EMPTY, Markers.EMPTY, Path.of("go.mod"), null, false, null, null,
          List.of(
            padded(directive("", "module", value(" ", "example.com/foo")), ""),
            padded(directive("\n\n", "go", value(" ", "1.22")), ""),
            padded(new GoMod.Block(randomId(), Space.format("\n\n"), Markers.EMPTY, "require", Space.SINGLE_SPACE,
              List.of(
                padded(directive("\n\t", "", value("", "github.com/a/b"), value(" ", "v1.0.0")), ""),
                padded(directive("\n\t", "", value("", "github.com/c/d"), value(" ", "v1.2.3")), " // indirect")
              ),
              Space.format("\n")), "")
          ),
          Space.format("\n"));

        assertThat(LineCounters.count(goMod)).isEqualTo(8);
        assertThat(GoRewriteRpc.get()).isNull();
    }

    @Test
    void countsGoSumWithoutStartingGoEngine() {
        var goSum = new GoSum(randomId(), Space.EMPTY, Markers.EMPTY, Path.of("go.sum"), null, false, null, null,
          List.of(
            new JRightPadded<>(sumLine("", false, "h1:abc="), Space.EMPTY, Markers.EMPTY),
            new JRightPadded<>(sumLine("\n", true, "h1:def="), Space.EMPTY, Markers.EMPTY)
          ),
          Space.EMPTY);

        assertThat(LineCounters.count(goSum)).isEqualTo(2);
        assertThat(GoRewriteRpc.get()).isNull();
    }

    private static JRightPadded<GoMod.GoModStatement> padded(GoMod.GoModStatement statement, String after) {
        return new JRightPadded<>(statement, Space.format(after), Markers.EMPTY);
    }

    private static GoMod.Directive directive(String prefix, String keyword, GoMod.Value... values) {
        return new GoMod.Directive(randomId(), Space.format(prefix), Markers.EMPTY, keyword, List.of(values));
    }

    private static GoMod.Value value(String prefix, String text) {
        return new GoMod.Value(randomId(), Space.format(prefix), Markers.EMPTY, text);
    }

    private static GoSum.Line sumLine(String prefix, boolean goMod, String hash) {
        return new GoSum.Line(randomId(), Space.format(prefix), Markers.EMPTY, "github.com/a/b", "v1.0.0", goMod, hash);
    }

    private long lineCount(SourceSpecs source) {
        var lineCount = new AtomicLong(-1);
        rewriteRun(
          spec -> spec
            .recipe(new FindOrganizationStatistics())
            .dataTable(OrganizationStatistics.Row.class, rows ->
              lineCount.set(rows.getFirst().getLineCount())),
          source
        );
        return lineCount.get();
    }
}
