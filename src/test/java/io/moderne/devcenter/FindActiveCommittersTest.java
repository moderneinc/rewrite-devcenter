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

import org.junit.jupiter.api.Test;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.table.DistinctCommitters;
import org.openrewrite.test.RewriteTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.test.SourceSpecs.text;

class FindActiveCommittersTest implements RewriteTest {

    @Test
    void reportsOnlyCommittersInsideTheWindow() {
        LocalDate today = LocalDate.now();
        var rows = committerRows(
          committer("Recent Author", "recent@example.com", today.minusDays(10)),
          committer("Edge Author", "edge@example.com", today.minusDays(90)),
          committer("Old Author", "old@example.com", today.minusDays(91)),
          committer("Ancient Author", "ancient@example.com", today.minusDays(450))
        );

        assertThat(rows)
          .extracting(DistinctCommitters.Row::getEmail)
          .containsExactly("recent@example.com", "edge@example.com");
    }

    @Test
    void committersWithoutPerDayDetailAreKept() {
        var rows = committerRows(
          new GitProvenance.Committer("John Doe", "john.doe@example.com", new TreeMap<>())
        );

        assertThat(rows).satisfiesExactly(row -> {
            assertThat(row.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(row.getLastCommit()).isNull();
        });
    }

    private List<DistinctCommitters.Row> committerRows(GitProvenance.Committer... committers) {
        var git = new GitProvenance(
          randomId(), "https://github.com/org/repo.git", "main", "123", null, null,
          List.of(committers)
        );

        var rows = new ArrayList<DistinctCommitters.Row>();
        rewriteRun(
          spec -> spec
            .recipe(new FindActiveCommitters())
            .dataTable(DistinctCommitters.Row.class, rows::addAll),
          text(
            "hi",
            spec -> spec.mapBeforeRecipe(pt -> pt.withMarkers(pt.getMarkers().add(git)))
          )
        );
        return rows;
    }

    private static GitProvenance.Committer committer(String name, String email, LocalDate lastCommit) {
        var commitsByDay = new TreeMap<LocalDate, Integer>();
        commitsByDay.put(lastCommit, 1);
        return new GitProvenance.Committer(name, email, commitsByDay);
    }
}
