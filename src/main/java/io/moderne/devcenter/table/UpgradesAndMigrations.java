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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UpgradesAndMigrations extends DataTable<UpgradesAndMigrations.Row> {
    /**
     * The community group name that all {@link io.moderne.devcenter.UpgradeMigrationCard}
     * subclasses use to share a single data table bucket. Downstream consumers (the Moderne
     * CLI and SaaS worker) can reference this constant to compose the on-disk CSV filename
     * via {@code CsvDataTableStore.fileKey()} without duplicating the literal.
     */
    public static final String GROUP = "io.moderne.devcenter.upgradesAndMigrations";

    private static final String BEST_ROWS_KEY = UpgradesAndMigrations.class.getName() + ".bestRows";

    private Map<String, Row> getBestRows(ExecutionContext ctx) {
        return ctx.computeMessageIfAbsent(BEST_ROWS_KEY, k -> new ConcurrentHashMap<>());
    }

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
    protected boolean allowWritingInThisCycle(ExecutionContext ctx) {
        // We override the default ("cycle <= 1") because some cards need to write
        // in a later cycle:
        //
        //   {@link io.moderne.devcenter.BucketedMetricCard} reads rows from an
        //   upstream data table and can only aggregate after that table has been
        //   fully populated. It defers its insert to cycle 2 (and bumps the
        //   scheduler via {@code DevCenter.CYCLE_TRIGGER}). The default
        //   {@code allowWritingInThisCycle} would silently drop those writes.
        //
        // Returning {@code true} is safe for the existing cards (JavaVersionUpgrade,
        // JUnitJupiterUpgrade, BuildToolCard, etc.) — none of them currently override
        // {@link Recipe#causesAnotherCycle()}, so they only fire in cycle 1. Even if
        // they did re-run in a later cycle (because some sibling recipe triggers
        // another cycle), the {@link #insertRow(ExecutionContext, Row)} override
        // below already dedupes via {@link #bestRow(Row, Row)} keyed on the card
        // name: a card recomputing the same measure produces an equivalent Row and
        // {@code bestRow} returns the existing entry, so no duplicate is written
        // to the underlying store.
        return true;
    }

    @Override
    public void insertRow(ExecutionContext ctx, Row row) {
        boolean[] improved = {false};
        getBestRows(ctx).compute(row.getCard(), (card, prev) -> {
            if (prev == null) {
                improved[0] = true;
                return row;
            }
            Row best = bestRow(prev, row);
            if (best != prev && best.getOrdinal() < prev.getOrdinal()) {
                improved[0] = true;
            }
            return best;
        });
        if (improved[0]) {
            super.insertRow(ctx, row);
        }
    }

    public static Row bestRow(Row a, Row b) {
        if (a.getOrdinal() < b.getOrdinal()) {
            return a;
        }
        if (a.getOrdinal() > b.getOrdinal()) {
            return b;
        }
        if (!Objects.equals(a.getCurrentMinimumVersion(), b.getCurrentMinimumVersion()) &&
            new LatestRelease(null).compare(null,
                    a.getCurrentMinimumVersion() == null ? "999" : a.getCurrentMinimumVersion(),
                    b.getCurrentMinimumVersion()) > 0) {
            return b;
        }
        return a;
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
