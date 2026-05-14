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

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

/**
 * Test-only data table used to exercise {@link BucketedMetricCard}.
 */
public class LcomTable extends DataTable<LcomTable.Row> {
    public LcomTable(Recipe recipe) {
        super(recipe, "Lack of cohesion", "Per-class LCOM4 values.");
    }

    @Value
    public static class Row {
        @Column(displayName = "lcom4", description = "Lack of cohesion of methods, version 4.")
        double lcom4;
    }
}
