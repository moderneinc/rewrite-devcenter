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
import org.openrewrite.csharp.marker.MSBuildProject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class CSharpVersionUpgrade extends UpgradeMigrationCard {

    // Matches modern TFMs: net5.0, net6.0, net7.0, net8.0, net9.0
    private static final Pattern MODERN_TFM_PATTERN =
            Pattern.compile("^net(\\d+)\\.\\d+$");

    // Matches .NET Core TFMs: netcoreapp2.1, netcoreapp3.1
    private static final Pattern CORE_TFM_PATTERN =
            Pattern.compile("^netcoreapp(\\d+)\\.\\d+$");

    @Option(displayName = "Major version",
            description = "The major version of .NET to upgrade to.",
            example = "9")
    int majorVersion;

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later .NET version";

    @Override
    public String getInstanceName() {
        return "Move to .NET " + majorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired .NET version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(MSBuildProject.class).ifPresent(project -> {
                        List<MSBuildProject.TargetFramework> targetFrameworks = project.getTargetFrameworks();
                        if (targetFrameworks != null && !targetFrameworks.isEmpty()) {
                            int minVersion = Integer.MAX_VALUE;
                            String minTfm = null;
                            for (MSBuildProject.TargetFramework tf : targetFrameworks) {
                                int version = parseMajorVersion(tf.getTargetFramework());
                                if (version >= 0 && version < minVersion) {
                                    minVersion = version;
                                    minTfm = tf.getTargetFramework();
                                }
                            }

                            if (minTfm != null) {
                                Measure measure = Measure.Completed;
                                if (minVersion < majorVersion) {
                                    if (minVersion >= 9) {
                                        measure = Measure.DotNet9Plus;
                                    } else if (minVersion >= 8) {
                                        measure = Measure.DotNet8Plus;
                                    } else if (minVersion >= 7) {
                                        measure = Measure.DotNet7Plus;
                                    } else if (minVersion >= 6) {
                                        measure = Measure.DotNet6Plus;
                                    } else {
                                        measure = Measure.DotNetFramework;
                                    }
                                }

                                upgradesAndMigrations.insertRow(ctx, CSharpVersionUpgrade.this,
                                        measure, minTfm);
                            }
                        }
                    });
                }
                return tree;
            }
        };
    }

    static int parseMajorVersion(String tfm) {
        Matcher m = MODERN_TFM_PATTERN.matcher(tfm);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        m = CORE_TFM_PATTERN.matcher(tfm);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        // Legacy .NET Framework TFMs like "net48", "net472", "net461"
        // and .NET Standard like "netstandard2.0" are all pre-.NET 5
        if (tfm.startsWith("net") && !tfm.contains(".")) {
            return 0;
        }
        if (tfm.startsWith("netstandard")) {
            return 0;
        }
        return -1;
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
        DotNetFramework(".NET Framework", "Legacy .NET Framework or .NET Core (pre-.NET 5).", 0),
        DotNet6Plus(".NET 6+", ".NET 6 and later", 6),
        DotNet7Plus(".NET 7+", ".NET 7 and later", 7),
        DotNet8Plus(".NET 8+", ".NET 8 and later", 8),
        DotNet9Plus(".NET 9+", ".NET 9 and later", 9),
        Completed("Completed", "The upgrade to the desired .NET version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajorVersion;
    }
}
