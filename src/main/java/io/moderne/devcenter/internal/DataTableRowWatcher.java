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

import org.openrewrite.DataTable;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.DataTableStore;
import org.openrewrite.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class DataTableRowWatcher<Row> {
    private final DataTable<Row> dataTable;
    private final ExecutionContext ctx;
    private int snapshotSize;

    public DataTableRowWatcher(DataTable<Row> dataTable, ExecutionContext ctx) {
        this.dataTable = dataTable;
        this.ctx = ctx;
    }

    public void start() {
        DataTableStore store = DataTableExecutionContextView.view(ctx).getDataTableStore();
        snapshotSize = (int) store.getRows(dataTable.getName(), dataTable.getGroup()).count();
    }

    @SuppressWarnings("unchecked")
    public List<Row> stop() {
        DataTableStore store = DataTableExecutionContextView.view(ctx).getDataTableStore();
        List<Row> allRows = (List<Row>) store.getRows(dataTable.getName(), dataTable.getGroup())
                .collect(toList());
        if (allRows.size() > snapshotSize) {
            return new ArrayList<>(allRows.subList(snapshotSize, allRows.size()));
        }
        return new ArrayList<>();
    }
}
