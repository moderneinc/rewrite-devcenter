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
package io.moderne.devcenter;

import io.moderne.devcenter.table.OrganizationStatistics;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.table.DistinctCommitters;
import org.openrewrite.test.RewriteTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.Assertions.java;

class OrganizationStatisticsReportTest implements RewriteTest {

    private final Recipe report = Environment.builder()
      .scanRuntimeClasspath("org.openrewrite")
      .scanYamlResources()
      .build()
      .activateRecipes("io.moderne.devcenter.OrganizationStatisticsReport");

    @Test
    void isNotADevCenter() {
        assertThat(DevCenter.isDevCenter(report.getDescriptor())).isFalse();
    }

    @Test
    void reportsLinesOfCodeAndActiveCommitters() {
        var commitsByDay = new TreeMap<LocalDate, Integer>();
        commitsByDay.put(LocalDate.now().minusDays(1), 1);
        var git = new GitProvenance(
          randomId(), "https://github.com/org/repo.git", "main", "123", null, null,
          List.of(new GitProvenance.Committer("Recent Author", "recent@example.com", commitsByDay))
        );

        var lineCounts = new ArrayList<OrganizationStatistics.Row>();
        var committers = new ArrayList<DistinctCommitters.Row>();
        rewriteRun(
          spec -> spec
            .recipe(report)
            .dataTable(OrganizationStatistics.Row.class, lineCounts::addAll)
            .dataTable(DistinctCommitters.Row.class, committers::addAll),
          java(
            """
              class Test {
                  void method() {}
              }
              """,
            spec -> spec.mapBeforeRecipe(cu -> cu.withMarkers(cu.getMarkers().add(git)))
          )
        );

        assertThat(lineCounts).extracting(OrganizationStatistics.Row::getLineCount).containsExactly(3L);
        assertThat(committers).extracting(DistinctCommitters.Row::getEmail).containsExactly("recent@example.com");
    }
}
