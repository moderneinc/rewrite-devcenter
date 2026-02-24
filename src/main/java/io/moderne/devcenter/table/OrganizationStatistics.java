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

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class OrganizationStatistics extends DataTable<OrganizationStatistics.Row> {

    public OrganizationStatistics(Recipe recipe) {
        super(recipe, "Organization statistics",
                "Per-repository statistics aggregated at the organization level.");
    }

    @Value
    public static class Row {
        @Column(
                displayName = "Line count",
                description = "The number of lines of code in this repository."
        )
        long lineCount;
    }
}
