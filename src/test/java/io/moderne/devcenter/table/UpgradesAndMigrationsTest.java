package io.moderne.devcenter.table;

import io.moderne.devcenter.LibraryUpgrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.DataTable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;

import java.util.List;
import java.util.Map;

import static io.moderne.devcenter.LibraryUpgrade.Measure.Major;
import static io.moderne.devcenter.LibraryUpgrade.Measure.Minor;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

public class UpgradesAndMigrationsTest {
    ExecutionContext ctx;
    UpgradesAndMigrations um;

    @BeforeEach
    void before() {
        um = new UpgradesAndMigrations(Recipe.noop());
        ctx = new InMemoryExecutionContext();
    }

    @Test
    void onlyLeastOrdinalRowRetained() {
        um.insertRow(ctx, row(Minor, "2.1.0"));
        um.insertRow(ctx, row(Major, "1.0.0"));
        um.insertRow(ctx, row(Minor, "2.2.0"));

        assertThat(rows()).containsExactly(row(Major, "1.0.0"));
    }

    @Test
    void leastCurrentMinimumVersionRetained() {
        um.insertRow(ctx, row(Minor, "2.4.0"));
        um.insertRow(ctx, row(Minor, "2.2.0"));
        um.insertRow(ctx, row(Minor, "2.3.0"));

        assertThat(rows()).containsExactly(row(Minor, "2.2.0"));
    }

    private List<UpgradesAndMigrations.Row> rows() {
        return ctx.<Map<DataTable<?>, List<UpgradesAndMigrations.Row>>>getMessage(
          "org.openrewrite.dataTables", emptyMap()).get(um);
    }

    private static UpgradesAndMigrations.Row row(LibraryUpgrade.Measure measure, String version) {
        return new UpgradesAndMigrations.Row(
          "cardName",
          measure.ordinal(),
          measure.toString(),
          version
        );
    }
}
