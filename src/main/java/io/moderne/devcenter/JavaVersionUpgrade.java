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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class JavaVersionUpgrade extends UpgradeMigrationCard {
    @Option(displayName = "Major version",
            description = "The major version of Java to upgrade to.",
            example = "24")
    int majorVersion;

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            example = "org.openrewrite.java.migrate.UpgradeToJava21",
            required = false)
    @Nullable
    String upgradeRecipe;

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

                    upgradesAndMigrations.insertRow(ctx, JavaVersionUpgrade.this,
                            measure, javaVersion.getSourceCompatibility());
                });
                return tree;
            }
        };
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Stream.of(Measure.values())
                .filter(measure -> measure.minimumMajorVersion < majorVersion)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        Java8Plus("Java 8+", "Technically, this is any version less than 11.", 8),
        Java11Plus("Java 11+", "Java 11 and later", 11),
        Java17Plus("Java 17+", "Java 17 and later", 17),
        Java21Plus("Java 21+", "Java 21 and later", 21),
        Completed("Completed", "The upgrade to the desired Java version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajorVersion;
    }
}
