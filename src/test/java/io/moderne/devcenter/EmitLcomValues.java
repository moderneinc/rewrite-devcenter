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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.util.List;

/**
 * Test-only recipe that pushes a fixed series of doubles into {@link LcomTable}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class EmitLcomValues extends Recipe {
    transient LcomTable lcom = new LcomTable(this);

    @Option(displayName = "Values",
            description = "Values to emit into the LCOM table.",
            example = "[2.0, 4.0, 6.0]")
    List<Double> values;

    @Override
    public String getDisplayName() {
        return "Emit LCOM rows";
    }

    @Override
    public String getDescription() {
        return "Emit a fixed series of LCOM values into the data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                for (Double value : values) {
                    lcom.insertRow(ctx, new LcomTable.Row(value));
                }
                return tree;
            }
        };
    }
}
