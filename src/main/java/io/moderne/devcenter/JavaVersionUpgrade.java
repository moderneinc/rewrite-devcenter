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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class JavaVersionUpgrade extends Recipe {
    transient UpgradesAndMigrations upgradesAndMigrations = new UpgradesAndMigrations(this);

    @Option(displayName = "Major version",
            description = "The major version of Java to upgrade to.",
            example = "24")
    int majorVersion;

    @Override
    public String getDisplayName() {
        return "Move to a later Java version";
    }

    @Override
    public String getInstanceName() {
        return "Move to Java " + majorVersion;
    }

    @Override
    public String getDescription() {
        return "Determine the current state of a repository relative to a desired Java version upgrade.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                tree.getMarkers().findFirst(JavaVersion.class).ifPresent(javaVersion -> {
                    Measure measure = Measure.Completed;
                    int actualVersion = javaVersion.getMajorVersion();
                    if (actualVersion < majorVersion) {
                        if (actualVersion >= 21) {
                            measure = Measure.Java21Plus;
                        } else if (actualVersion >= 17) {
                            measure = Measure.Java17Plus;
                        } else if (actualVersion >= 11) {
                            measure = Measure.Java11Plus;
                        } else {
                            // TODO To keep compatibility with the current visualization,
                            //  though the project's version may actually be on a version
                            //  less than 8.
                            measure = Measure.Java8Plus;
                        }
                    }

                    upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                            getInstanceName(),
                            measure.ordinal(),
                            measure.displayName,
                            javaVersion.getSourceCompatibility()
                    ));
                });
                return tree;
            }
        };
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure {
        Java8Plus("Java 8+"),
        Java11Plus("Java 11+"),
        Java17Plus("Java 17+"),
        Java21Plus("Java 21+"),
        Completed("Completed");

        private final String displayName;
    }
}
