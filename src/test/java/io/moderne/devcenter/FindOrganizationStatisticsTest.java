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

import io.moderne.devcenter.table.OrganizationStatistics;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
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

    private long lineCount(SourceSpecs source) {
        AtomicLong lineCount = new AtomicLong(-1);
        rewriteRun(
          spec -> spec
            .recipe(new FindOrganizationStatistics())
            .dataTable(OrganizationStatistics.Row.class, rows ->
              lineCount.set(rows.get(0).getLineCount())),
          source
        );
        return lineCount.get();
    }
}
