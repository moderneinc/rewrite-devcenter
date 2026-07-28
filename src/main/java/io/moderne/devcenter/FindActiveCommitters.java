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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Recipe;
import org.openrewrite.search.FindCommitters;

import java.time.LocalDate;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Runs {@link FindCommitters} over a rolling window rather than a repository's whole history.
 * <p>
 * The {@code fromDate} option of {@link FindCommitters} takes an absolute date, so a literal in a
 * DevCenter configuration would widen the window by a day, every day. Computing the cutoff per run keeps
 * the window's length fixed. Reporting is left to {@link FindCommitters}, so both of its data tables are
 * produced as before, only bounded.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class FindActiveCommitters extends Recipe {
    private static final int WITHIN_LAST_DAYS = 90;

    String displayName = "Find active committers on repositories";

    String description = "List the committers on a repository whose most recent commit falls within the " +
                         "last 90 days, for the DevCenter contributing developers statistic.";

    @Override
    public List<Recipe> getRecipeList() {
        LocalDate cutoff = LocalDate.now().minusDays(WITHIN_LAST_DAYS);
        return singletonList(new FindCommitters(cutoff.toString()));
    }
}
