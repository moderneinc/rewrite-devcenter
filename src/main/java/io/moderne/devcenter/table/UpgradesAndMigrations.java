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
import org.openrewrite.*;
import org.openrewrite.semver.LatestRelease;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
                                                       E measure,
                                                       String currentMinimumVersion) {
        insertRow(ctx, new UpgradesAndMigrations.Row(
                recipe.getInstanceName(),
                recipe.ordinal(measure),
                measure.getName(),
                currentMinimumVersion
        ));
    }

    @Override
    public void insertRow(ExecutionContext ctx, Row row) {
        ensureDeduplicatingStore(ctx);
        if (ctx.getMessage(ExecutionContext.CURRENT_CYCLE) == null || allowWritingInThisCycle(ctx)) {
            DataTableExecutionContextView.view(ctx).getDataTableStore()
                    .insertRow(this, ctx, row);
        }
    }

    private static void ensureDeduplicatingStore(ExecutionContext ctx) {
        DataTableExecutionContextView view = DataTableExecutionContextView.view(ctx);
        DataTableStore store = view.getDataTableStore();
        if (!(store instanceof DeduplicatingDataTableStore)) {
            view.setDataTableStore(new DeduplicatingDataTableStore(store));
        }
    }

    static Row deduplicate(Row existing, Row incoming) {
        if (existing == null) {
            return incoming;
        }
        int minOrdinal = existing.getOrdinal();
        if (incoming.getOrdinal() > minOrdinal) {
            return existing;
        }
        if (incoming.getOrdinal() < minOrdinal) {
            return incoming;
        }
        // Same ordinal — keep the row with the lower version
        String existingVersion = existing.getCurrentMinimumVersion();
        String incomingVersion = incoming.getCurrentMinimumVersion();
        if (Objects.equals(existingVersion, incomingVersion)) {
            return existing;
        }
        if (incomingVersion != null && (existingVersion == null ||
            new LatestRelease(null).compare(null, existingVersion, incomingVersion) > 0)) {
            return incoming;
        }
        return existing;
    }

    /**
     * DataTableStore wrapper that deduplicates UpgradesAndMigrations rows when they are read.
     * Rows are stored as-is (append-only), and deduplication happens at read time by keeping
     * only the best row per card (lowest ordinal, lowest version for same ordinal).
     */
    private static class DeduplicatingDataTableStore implements DataTableStore {
        private final DataTableStore delegate;

        DeduplicatingDataTableStore(DataTableStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public <R> void insertRow(DataTable<R> dataTable, ExecutionContext ctx, R row) {
            delegate.insertRow(dataTable, ctx, row);
        }

        @Override
        public Stream<?> getRows(String name, String instanceName) {
            Stream<?> rows = delegate.getRows(name, instanceName);
            if (UpgradesAndMigrations.class.getName().equals(name)) {
                return deduplicateRows(rows);
            }
            return rows;
        }

        @Override
        public Collection<DataTable<?>> getDataTables() {
            return delegate.getDataTables();
        }

        @SuppressWarnings("unchecked")
        private Stream<?> deduplicateRows(Stream<?> rows) {
            Map<String, Row> bestPerCard = new LinkedHashMap<>();
            rows.forEach(r -> bestPerCard.merge(((Row) r).getCard(), (Row) r,
                    UpgradesAndMigrations::deduplicate));
            return bestPerCard.values().stream();
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
