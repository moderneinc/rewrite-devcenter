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
package io.moderne.devcenter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.golang.marker.GoResolutionResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class GoVersionUpgrade extends UpgradeMigrationCard {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)");

    @Option(displayName = "Minor version",
            description = "The minor version of Go (1.x) to upgrade to.",
            example = "26")
    int minorVersion;

    @Option(example = "org.openrewrite.golang.migration.UpgradeGoTo126", displayName = "Upgrade recipe",
        description = "The recipe to use to upgrade.",
        required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Go version";

    @Override
    public String getInstanceName() {
        return "Move to Go 1." + minorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Go version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(GoResolutionResult.class).ifPresent(result -> {
                        String goVersion = result.getGoVersion();
                        if (goVersion != null) {
                            int[] version = parseVersion(goVersion);
                            if (version != null) {
                                int major = version[0];
                                int minor = version[1];

                                Measure measure = Measure.Completed;
                                if (major == 1 && minor < minorVersion) {
                                    if (minor >= 26) {
                                        measure = Measure.Go126Plus;
                                    } else if (minor >= 25) {
                                        measure = Measure.Go125Plus;
                                    } else if (minor >= 24) {
                                        measure = Measure.Go124Plus;
                                    } else if (minor >= 23) {
                                        measure = Measure.Go123Plus;
                                    } else if (minor >= 22) {
                                        measure = Measure.Go122Plus;
                                    } else if (minor >= 21) {
                                        measure = Measure.Go121Plus;
                                    } else if (minor >= 20) {
                                        measure = Measure.Go120Plus;
                                    } else if (minor >= 19) {
                                        measure = Measure.Go119Plus;
                                    } else if (minor >= 18) {
                                        measure = Measure.Go118Plus;
                                    } else {
                                        measure = Measure.GoPre118;
                                    }
                                }

                                upgradesAndMigrations.insertRow(ctx, GoVersionUpgrade.this,
                                        measure, major + "." + minor);
                            }
                        }
                    });
                }
                return tree;
            }
        };
    }

    static int @Nullable [] parseVersion(String goVersion) {
        Matcher m = VERSION_PATTERN.matcher(goVersion);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Stream.of(Measure.values())
                .filter(measure -> measure.minimumMinor < minorVersion)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        GoPre118("Go 1.17 or earlier", "Go 1.17 and earlier (pre-generics).", 0),
        Go118Plus("Go 1.18+", "Go 1.18 and later", 18),
        Go119Plus("Go 1.19+", "Go 1.19 and later", 19),
        Go120Plus("Go 1.20+", "Go 1.20 and later", 20),
        Go121Plus("Go 1.21+", "Go 1.21 and later", 21),
        Go122Plus("Go 1.22+", "Go 1.22 and later", 22),
        Go123Plus("Go 1.23+", "Go 1.23 and later", 23),
        Go124Plus("Go 1.24+", "Go 1.24 and later", 24),
        Go125Plus("Go 1.25+", "Go 1.25 and later", 25),
        Go126Plus("Go 1.26+", "Go 1.26 and later", 26),
        Completed("Completed", "The upgrade to the desired Go version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMinor;
    }
}
