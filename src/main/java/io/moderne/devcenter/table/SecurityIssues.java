package io.moderne.devcenter.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class SecurityIssues extends DataTable<SecurityIssues.Row> {

    public SecurityIssues(Recipe recipe) {
        super(recipe,
                "Security issues",
                "Security issues in the repository."
        );
    }

    @Value
    public static class Row {
        @Column(
                displayName = "Issue name",
                description = "The name of the security issue."
        )
        String issueName;
    }
}
