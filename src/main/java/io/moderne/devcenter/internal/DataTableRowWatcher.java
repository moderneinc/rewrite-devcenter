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
        return rows.subList(0, rows.size());
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
