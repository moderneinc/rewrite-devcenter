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
package io.moderne.devcenter.internal;

import org.jspecify.annotations.Nullable;
import org.openrewrite.DataTable;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.DataTableStore;
import org.openrewrite.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DataTableRowWatcher<Row> {
    private final DataTable<Row> dataTable;
    private final ExecutionContext ctx;
    private final List<Row> captured = new ArrayList<>();
    private @Nullable DataTableStore originalStore;

    public DataTableRowWatcher(DataTable<Row> dataTable, ExecutionContext ctx) {
        this.dataTable = dataTable;
        this.ctx = ctx;
    }

    public void start() {
        DataTableExecutionContextView view = DataTableExecutionContextView.view(ctx);
        originalStore = view.getDataTableStore();
        view.setDataTableStore(new RecordingStore(originalStore));
    }

    public List<Row> stop() {
        DataTableExecutionContextView.view(ctx).setDataTableStore(
                Objects.requireNonNull(originalStore, "stop() called before start()"));
        return new ArrayList<>(captured);
    }

    private boolean matchesTarget(DataTable<?> table) {
        if (!table.getName().equals(dataTable.getName())) {
            return false;
        }
        return Objects.equals(bucketKey(table), bucketKey(dataTable));
    }

    private static @Nullable String bucketKey(DataTable<?> table) {
        return table.getGroup() != null ? table.getGroup() : table.getInstanceName();
    }

    private final class RecordingStore implements DataTableStore {
        private final DataTableStore delegate;

        RecordingStore(DataTableStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public <R> void insertRow(DataTable<R> table, ExecutionContext ctx, R row) {
            delegate.insertRow(table, ctx, row);
            if (matchesTarget(table)) {
                @SuppressWarnings("unchecked")
                Row casted = (Row) row;
                captured.add(casted);
            }
        }

        @Override
        public Stream<?> getRows(String dataTableName, @Nullable String group) {
            return delegate.getRows(dataTableName, group);
        }

        @Override
        public Collection<DataTable<?>> getDataTables() {
            return delegate.getDataTables();
        }
    }
}
