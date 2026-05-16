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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.DataTableStore;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A DevCenter upgrade/migration card that bins a repository into one of a fixed
 * set of buckets based on an aggregation over a previously-emitted data table.
 * <p>
 * The card composes two steps:
 * <ol>
 *   <li>An {@link AggregationFunction} reduces the values of the configured
 *       {@link #column} across all rows of the upstream data table to a single
 *       representative value.</li>
 *   <li>That value is then assigned to a {@link Bucket} using
 *       {@link Bucket#match(double, Bucket[])}.</li>
 * </ol>
 * The card is intended to run after a data-table-producing recipe in the same
 * recipe list. Aggregation is deferred to cycle 2 so the upstream data table is
 * fully populated for this repository before we read it. Cycle 1's visitor only
 * pings {@link DevCenter#CYCLE_TRIGGER} so the scheduler enrolls us for cycle 2
 * even when no recipe in the list edits any source files.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class BucketedMetricCard extends UpgradeMigrationCard {

    @Option(displayName = "Input data table",
            description = "The fully qualified class name of the data table to read rows from. " +
                          "This data table is expected to be populated by another recipe earlier in the same recipe list.",
            example = "io.moderne.organizations.table.ClassCohesion")
    String inputDataTable;

    @Option(displayName = "Card name",
            description = "The display name of this DevCenter card.",
            example = "Class cohesion (LCOM4)")
    String cardName;

    @Option(displayName = "Column",
            description = "The numeric column of the input data table to feed into the aggregation function.",
            example = "lcom4")
    String column;

    @Option(displayName = "Aggregation",
            description = "How to reduce the column's values across all rows in the repository to a " +
                          "single representative value before bucketing. `MIN`, `MAX`, `SUM`, and " +
                          "`AVERAGE` operate on numeric values of the column. `COUNT` returns the " +
                          "number of rows the upstream data table emitted for this repository, " +
                          "regardless of column value. `UNIQUE` returns the number of distinct " +
                          "non-null values of the column. Matching is case-insensitive.",
            valid = {"MIN", "MAX", "SUM", "AVERAGE", "COUNT", "UNIQUE"},
            example = "AVERAGE")
    AggregationFunction aggregation;

    @Option(displayName = "Buckets",
            description = "Ordered list of buckets. Each bucket has a `name` and an inclusive lower " +
                          "bound `moreThan` — a value is considered to fall into the bucket when " +
                          "`value >= moreThan`. When multiple buckets apply, the one with the largest " +
                          "`moreThan` wins. The list order defines the DevCenter measure ordinal: the " +
                          "first bucket maps to ordinal `0`, the last to ordinal `size - 1`. This lets " +
                          "callers control which end of the scale the visualization renders as " +
                          "\"worst\" (lowest ordinal) versus \"best\" (highest ordinal).",
            example = "[{\"name\": \"LOW\", \"moreThan\": 10}, " +
                      "{\"name\": \"MEDIUM\", \"moreThan\": 3}, " +
                      "{\"name\": \"HIGH\", \"moreThan\": 0}]")
    Bucket[] buckets;

    String displayName = "DevCenter card from a data table column";

    @Override
    public String getInstanceName() {
        return cardName;
    }

    String description = "Read rows from a previously emitted data table, aggregate a numeric column across " +
            "all rows for this repository, and bucket the result into ordinal DevCenter measures.";

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return null;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        List<DevCenterMeasure> measures = new ArrayList<>(buckets.length);
        for (int i = 0; i < buckets.length; i++) {
            measures.add(new BucketMeasure(buckets[i].getName(), i));
        }
        return measures;
    }

    @Override
    public int ordinal(DevCenterMeasure measure) {
        if (measure instanceof BucketMeasure) {
            return ((BucketMeasure) measure).getOrdinal();
        }
        return super.ordinal(measure);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (ctx.getCycle() == 1) {
                    // Defer aggregation until cycle 2 so the upstream data table is fully
                    // populated. Ping CYCLE_TRIGGER so the scheduler enrolls us for another
                    // cycle even if no other recipe in this list edits any source.
                    ctx.putMessage(DevCenter.CYCLE_TRIGGER, true);
                    return tree;
                }
                AtomicBoolean inserted = ctx.computeMessageIfAbsent(
                        BucketedMetricCard.class.getName() + ".inserted." + cardName,
                        k -> new AtomicBoolean(false));
                if (!inserted.compareAndSet(false, true)) {
                    return tree;
                }

                List<Object> values = extractColumnValues(collectRows(ctx));
                Double aggregated = aggregation.apply(values);
                if (aggregated == null) {
                    return tree;
                }
                Bucket bucket = Bucket.match(aggregated, buckets);
                if (bucket == null) {
                    return tree;
                }
                int ordinal = indexOf(bucket);
                upgradesAndMigrations.insertRow(ctx, BucketedMetricCard.this,
                        new BucketMeasure(bucket.getName(), ordinal), null);
                return tree;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<Object> collectRows(ExecutionContext ctx) {
        DataTableStore store = DataTableExecutionContextView.view(ctx).getDataTableStore();
        List<Object> rows = new ArrayList<>();
        for (DataTable<?> dt : store.getDataTables()) {
            if (dt.getClass().getName().equals(inputDataTable)) {
                Class<? extends DataTable<Object>> dtClass = (Class<? extends DataTable<Object>>) dt.getClass();
                store.getRows(dtClass, dt.getGroup()).forEach(rows::add);
            }
        }
        return rows;
    }

    private List<Object> extractColumnValues(List<Object> rows) {
        List<Object> values = new ArrayList<>(rows.size());
        for (Object row : rows) {
            values.add(extractRawValue(row));
        }
        return values;
    }

    private @Nullable Object extractRawValue(Object row) {
        for (Field field : row.getClass().getDeclaredFields()) {
            Column annotation = field.getAnnotation(Column.class);
            String displayName = annotation == null ? null : annotation.displayName();
            if (!column.equalsIgnoreCase(field.getName()) &&
                (displayName == null || !column.equalsIgnoreCase(displayName))) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(row);
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }

    private int indexOf(Bucket bucket) {
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] == bucket) {
                return i;
            }
        }
        return -1;
    }

    @Value
    public static class BucketMeasure implements DevCenterMeasure {
        @Language("markdown")
        String name;

        int ordinal;

        @Override
        public int ordinal() {
            return ordinal;
        }

        @Override
        @Language("markdown")
        public String getDescription() {
            return "Aggregated column value falls within the \"" + name + "\" bucket.";
        }
    }
}
