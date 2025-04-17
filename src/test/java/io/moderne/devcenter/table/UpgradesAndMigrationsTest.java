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
package io.moderne.devcenter.table;

import io.moderne.devcenter.LibraryUpgrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;

import java.util.List;
import java.util.Map;

import static io.moderne.devcenter.LibraryUpgrade.Measure.Major;
import static io.moderne.devcenter.LibraryUpgrade.Measure.Minor;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

public class UpgradesAndMigrationsTest {
    ExecutionContext ctx;
    UpgradesAndMigrations um;

    @BeforeEach
    void before() {
        um = new UpgradesAndMigrations(Recipe.noop());
        ctx = new InMemoryExecutionContext();
    }

    @Test
    void onlyLeastOrdinalRowRetained() {
        um.insertRow(ctx, row(Minor, "2.1.0"));
        um.insertRow(ctx, row(Major, "1.0.0"));
        um.insertRow(ctx, row(Minor, "2.2.0"));

        assertThat(rows()).containsExactly(row(Major, "1.0.0"));
    }

    @Test
    void leastCurrentMinimumVersionRetained() {
        um.insertRow(ctx, row(Minor, "2.4.0"));
        um.insertRow(ctx, row(Minor, "2.2.0"));
        um.insertRow(ctx, row(Minor, "2.3.0"));

        assertThat(rows()).containsExactly(row(Minor, "2.2.0"));
    }

    private List<UpgradesAndMigrations.Row> rows() {
        return ctx.<Map<DataTable<?>, List<UpgradesAndMigrations.Row>>>getMessage(
          "org.openrewrite.dataTables", emptyMap()).get(um);
    }

    private static UpgradesAndMigrations.Row row(LibraryUpgrade.Measure measure, String version) {
        return new UpgradesAndMigrations.Row(
          "cardName",
          measure.ordinal(),
          measure.toString(),
          version
        );
    }
}
