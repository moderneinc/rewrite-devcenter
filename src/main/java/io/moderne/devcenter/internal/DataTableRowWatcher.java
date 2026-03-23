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

import lombok.RequiredArgsConstructor;
import org.openrewrite.DataTable;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.ExecutionContext;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class DataTableRowWatcher<Row> {
    private final DataTable<Row> dataTable;
    private final ExecutionContext ctx;

    private int snapshotSize;

    public void start() {
        snapshotSize = (int) DataTableExecutionContextView.view(ctx).getDataTableStore()
                .getRows(dataTable.getName(), dataTable.getGroup())
                .count();
    }

    @SuppressWarnings("unchecked")
    public List<Row> stop() {
        List<Row> allRows = DataTableExecutionContextView.view(ctx).getDataTableStore()
                .getRows(dataTable.getName(), dataTable.getGroup())
                .map(r -> (Row) r)
                .collect(Collectors.toList());
        if (snapshotSize < allRows.size()) {
            return allRows.subList(snapshotSize, allRows.size());
        }
        return Collections.emptyList();
    }
}
