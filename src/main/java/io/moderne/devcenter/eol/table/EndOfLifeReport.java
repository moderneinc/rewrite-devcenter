/*
 * Copyright 2026 the original author or authors.
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
package io.moderne.devcenter.eol.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

/**
 * Per-item detail behind an aggregate end-of-life DevCenter card: one row for each dependency or
 * runtime matched against the EOL feed. Shared by every card (JVM, npm, NuGet, runtime) so detail
 * is consistent across ecosystems and can be filtered by the {@code Ecosystem}/{@code Kind} columns.
 */
public class EndOfLifeReport extends DataTable<EndOfLifeReport.Row> {

    public EndOfLifeReport(Recipe recipe) {
        super(recipe,
                "End of life",
                "End-of-life status of tracked dependencies and runtimes, sourced from the EOL feed.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Ecosystem",
                description = "The ecosystem the item was detected in (e.g. JVM, npm, NuGet, Java, Node.js, .NET).")
        String ecosystem;

        @Column(displayName = "Kind",
                description = "Whether the item is a dependency or a language runtime.")
        String kind;

        @Column(displayName = "Name",
                description = "The detected dependency or runtime (e.g. org.springframework.boot:spring-boot, express).")
        String name;

        @Column(displayName = "Version",
                description = "The resolved or declared version that was matched.")
        String version;

        @Column(displayName = "Product",
                description = "The EOL feed product the item was matched to.")
        String product;

        @Column(displayName = "Release cycle",
                description = "The release cycle the version falls into.")
        String cycle;

        @Column(displayName = "End-of-life date",
                description = "The end-of-life date of the release cycle, if published.")
        String eolDate;

        @Column(displayName = "Status",
                description = "The computed status: EndOfLife, EndOfLifeApproaching, or Supported.")
        String status;
    }
}
