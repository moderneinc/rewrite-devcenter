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

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

public class JUnitUpgrade extends Recipe {
    private final transient UpgradesAndMigrations upgradesAndMigrations = new UpgradesAndMigrations(this);

    @Override
    public String getDisplayName() {
        return "Move to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Move to JUnit Jupiter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                J j2 = (J) new FindAnnotations("@org.junit.Test", true)
                        .getVisitor().visitNonNull(tree, ctx);
                if (tree != j2) {
                    upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                            "Move to JUnit 5",
                            Measure.JUnit4.ordinal(),
                            "JUnit 4",
                            "JUnit 4"
                    ));
                }

                J j3 = (J) new FindAnnotations("@org.junit.jupiter.api.Test", true)
                        .getVisitor().visitNonNull(j2, ctx);
                if (tree != j3) {
                    upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                            "Move to JUnit 5",
                            Measure.Completed.ordinal(),
                            "Completed",
                            "JUnit 5"
                    ));
                }

                return j3;
            }
        };
    }

    public enum Measure {
        JUnit4,
        Completed
    }
}
