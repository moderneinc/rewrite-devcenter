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
import org.openrewrite.python.marker.PythonResolutionResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class PythonVersionUpgrade extends UpgradeMigrationCard {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?:>=|~=|==|>)\\s*(\\d+)\\.(\\d+)");
    private static final Pattern BARE_VERSION_PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)");

    @Option(displayName = "Minor version",
            description = "The minor version of Python 3 to upgrade to.",
            example = "13")
    int minorVersion;

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Python version";

    @Override
    public String getInstanceName() {
        return "Move to Python 3." + minorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Python version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(PythonResolutionResult.class).ifPresent(result -> {
                        String requiresPython = result.getRequiresPython();
                        if (requiresPython != null) {
                            int[] version = parseMinimumVersion(requiresPython);
                            if (version != null) {
                                int major = version[0];
                                int minor = version[1];

                                Measure measure = Measure.Completed;
                                if (major < 3 || (major == 3 && minor < minorVersion)) {
                                    if (major < 3) {
                                        measure = Measure.Python2;
                                    } else if (minor >= 13) {
                                        measure = Measure.Python313Plus;
                                    } else if (minor >= 12) {
                                        measure = Measure.Python312Plus;
                                    } else if (minor >= 11) {
                                        measure = Measure.Python311Plus;
                                    } else if (minor >= 10) {
                                        measure = Measure.Python310Plus;
                                    } else if (minor >= 9) {
                                        measure = Measure.Python39Plus;
                                    } else {
                                        measure = Measure.Python38Plus;
                                    }
                                }

                                upgradesAndMigrations.insertRow(ctx, PythonVersionUpgrade.this,
                                        measure, major + "." + minor);
                            }
                        }
                    });
                }
                return tree;
            }
        };
    }

    static int @Nullable [] parseMinimumVersion(String requiresPython) {
        Matcher m = VERSION_PATTERN.matcher(requiresPython);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        m = BARE_VERSION_PATTERN.matcher(requiresPython);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Stream.of(Measure.values())
                .filter(measure -> measure.minimumMinorVersion < minorVersion)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        Python2("Python 2", "Python 2.x (end of life).", 0),
        Python38Plus("Python 3.8+", "Python 3.8 and later", 8),
        Python39Plus("Python 3.9+", "Python 3.9 and later", 9),
        Python310Plus("Python 3.10+", "Python 3.10 and later", 10),
        Python311Plus("Python 3.11+", "Python 3.11 and later", 11),
        Python312Plus("Python 3.12+", "Python 3.12 and later", 12),
        Python313Plus("Python 3.13+", "Python 3.13 and later", 13),
        Completed("Completed", "The upgrade to the desired Python version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMinorVersion;
    }
}
