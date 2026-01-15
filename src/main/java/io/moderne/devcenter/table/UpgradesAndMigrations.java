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

import io.moderne.devcenter.DevCenterMeasure;
import io.moderne.devcenter.UpgradeMigrationCard;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.semver.LatestRelease;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.openrewrite.ExecutionContext.CURRENT_CYCLE;

public class UpgradesAndMigrations extends DataTable<UpgradesAndMigrations.Row> {
    @Language("markdown")
    private static final String DISPLAY_NAME = "Upgrades and migrations";

    @Language("markdown")
    private static final String DESCRIPTION = "Progress towards organizational objectives on library or language migrations and upgrades.";

    public UpgradesAndMigrations() {
        super(DISPLAY_NAME, DESCRIPTION);
    }

    public <T extends UpgradeMigrationCard> UpgradesAndMigrations(T recipe) {
        super(recipe, DISPLAY_NAME, DESCRIPTION);
    }

    public <E extends DevCenterMeasure> void insertRow(ExecutionContext ctx,
                                                       UpgradeMigrationCard recipe,
                                                       E measure, String currentMinimumVersion) {
        insertRow(ctx, new UpgradesAndMigrations.Row(
                recipe.getInstanceName(),
                recipe.ordinal(measure),
                measure.getName(),
                currentMinimumVersion
        ));
    }

    @Override
    public void insertRow(ExecutionContext ctx, Row row) {
        // TODO CURRENT_CYCLE value is null in the context of a RewriteTest.
        if (ctx.getMessage(CURRENT_CYCLE) == null || this.allowWritingInThisCycle(ctx)) {
            ctx.computeMessage("org.openrewrite.dataTables", row, ConcurrentHashMap<DataTable<?>, List<?>>::new, (extract, allDataTables) -> {
                List<Row> rows = getRows(allDataTables);
                int minOrdinal = rows.stream()
                        .filter(r -> r.getCard().equals(row.getCard()))
                        .mapToInt(Row::getOrdinal)
                        .min()
                        .orElse(Integer.MAX_VALUE);
                if (row.getOrdinal() <= minOrdinal) {
                    String currentMinVersion = rows.stream().map(Row::getCurrentMinimumVersion)
                            .filter(Objects::nonNull).findFirst().orElse(null);
                    if (row.getOrdinal() == minOrdinal && !row.getCurrentMinimumVersion().equals(currentMinVersion) &&
                        new LatestRelease(null).compare(null,
                                currentMinVersion == null ? "999" : currentMinVersion,
                                row.getCurrentMinimumVersion()) > 0) {
                        rows.removeIf(r -> row.getCard().equals(r.getCard())); // There can only be one!
                        rows.add(row);
                    } else if (row.getOrdinal() != minOrdinal) {
                        rows.removeIf(r -> row.getCard().equals(r.getCard())); // There can only be one!
                        rows.add(row);
                    }
                }
                return allDataTables;
            });
        }
    }

    // TODO We have some design challenges with DataTable where two DataTable of the same
    //  type show up as two different entries. I think only one ultimately is downloadable.
    private List<Row> getRows(Map<DataTable<?>, List<?>> dataTables) {
        for (Map.Entry<DataTable<?>, List<?>> dataTableEntry : dataTables.entrySet()) {
            if (dataTableEntry.getKey().getClass().equals(UpgradesAndMigrations.class)) {
                //noinspection unchecked
                return (List<Row>) dataTableEntry.getValue();
            }
        }
        //noinspection unchecked
        return (List<Row>) dataTables.computeIfAbsent(this,
                c -> new ArrayList<>());
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
