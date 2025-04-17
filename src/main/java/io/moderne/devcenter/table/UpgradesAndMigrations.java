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

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.semver.LatestRelease;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.openrewrite.ExecutionContext.CURRENT_CYCLE;

public class UpgradesAndMigrations extends DataTable<UpgradesAndMigrations.Row> {

    public UpgradesAndMigrations(Recipe recipe) {
        super(recipe,
                "Upgrades and migrations",
                "Progress towards organizational objectives on library or language migrations and upgrades."
        );
    }

    @Override
    public void insertRow(ExecutionContext ctx, Row row) {
        if (ctx.getMessage(CURRENT_CYCLE) == null || this.allowWritingInThisCycle(ctx)) {
            ctx.computeMessage("org.openrewrite.dataTables", row, ConcurrentHashMap::new, (extract, allDataTables) -> {
                //noinspection rawtypes,unchecked
                List<Row> dataTablesOfType = (List) allDataTables.computeIfAbsent(this, (c) -> new ArrayList());
                int minOrdinal = dataTablesOfType.stream()
                        .mapToInt(Row::getOrdinal)
                        .min()
                        .orElse(Integer.MAX_VALUE);
                if (row.getOrdinal() <= minOrdinal) {
                    String currentMinVersion = dataTablesOfType.stream().map(Row::getCurrentMinimumVersion).findFirst()
                            .orElse(null);
                    if (row.getOrdinal() == minOrdinal && !row.getCurrentMinimumVersion().equals(currentMinVersion) &&
                        new LatestRelease(null).compare(null,
                                currentMinVersion == null ? "999" : currentMinVersion,
                                row.getCurrentMinimumVersion()) > 0) {
                        dataTablesOfType.clear(); // There can only be one!
                        dataTablesOfType.add(row);
                    } else if (row.getOrdinal() != minOrdinal) {
                        dataTablesOfType.clear(); // There can only be one!
                        dataTablesOfType.add(row);
                    }
                }
                return allDataTables;
            });
        }
    }

    @Value
    public static class Row {
        @Column(
                displayName = "Card",
                description = "The display name of the DevCenter card"
        )
        String card;

        @Column(
                displayName = "Ordinal",
                description = "The ordinal position of this value relative to other values."
        )
        int ordinal;

        @Column(
                displayName = "Value",
                description = "The display value of the current state of this repository."
        )
        String value;

        @Column(displayName = "Minimum version",
                description = "The minimum matching version that is currently in use.")
        String currentMinimumVersion;
    }
}
