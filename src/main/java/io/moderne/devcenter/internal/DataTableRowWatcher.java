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
import org.openrewrite.ExecutionContext;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@RequiredArgsConstructor
public class DataTableRowWatcher<Row> {
    private final DataTable<Row> dataTable;
    private final ExecutionContext ctx;

    int startingRowCount;

    public void start() {
        startingRowCount = getRows().size();
    }

    public List<Row> stop() {
        List<Row> rows = getRows();
        return rows.subList(startingRowCount, rows.size());
    }

    private List<Row> getRows() {
        Map<DataTable<?>, List<?>> dataTables = ctx.getMessage("org.openrewrite.dataTables", emptyMap());
        for (Map.Entry<DataTable<?>, List<?>> dataTableEntry : dataTables.entrySet()) {
            if (dataTableEntry.getKey().getClass().equals(dataTable.getClass())) {
                //noinspection unchecked
                return (List<Row>) dataTableEntry.getValue();
            }
        }
        return emptyList();
    }
}
