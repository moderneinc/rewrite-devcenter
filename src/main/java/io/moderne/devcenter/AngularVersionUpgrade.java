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
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.javascript.marker.NodeResolutionResult.Dependency;
import org.openrewrite.javascript.marker.NodeResolutionResult.ResolvedDependency;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class AngularVersionUpgrade extends UpgradeMigrationCard {

    private static final String ANGULAR_CORE = "@angular/core";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?:>=|[~^]|>)?\\s*v?(\\d+)");

    @Option(displayName = "Major version",
            description = "The major version of Angular to upgrade to.",
            example = "21")
    int majorVersion;

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Angular version";

    @Override
    public String getInstanceName() {
        return "Move to Angular " + majorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Angular version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    ((SourceFile) tree).getMarkers().findFirst(NodeResolutionResult.class).ifPresent(result -> {
                        int actualVersion = findAngularCoreMajor(result);
                        if (actualVersion < 0) {
                            return;
                        }
                        upgradesAndMigrations.insertRow(ctx, AngularVersionUpgrade.this,
                                bucketFor(actualVersion, majorVersion), String.valueOf(actualVersion));
                    });
                }
                return tree;
            }
        };
    }

    static int findAngularCoreMajor(NodeResolutionResult result) {
        // Only @angular/core (Angular 2+). The legacy `angular` package is AngularJS 1.x,
        // a different product with no migration path through rewrite-angular.
        ResolvedDependency resolved = result.getResolvedDependency(ANGULAR_CORE);
        if (resolved != null) {
            int v = parseMajorVersion(resolved.getVersion());
            if (v >= 0) {
                return v;
            }
        }
        int fromConstraint = findInScope(result.getDependencies());
        if (fromConstraint >= 0) {
            return fromConstraint;
        }
        return findInScope(result.getDevDependencies());
    }

    private static int findInScope(List<Dependency> scope) {
        for (Dependency d : scope) {
            if (ANGULAR_CORE.equals(d.getName())) {
                int v = parseMajorVersion(d.getVersionConstraint());
                if (v >= 0) {
                    return v;
                }
            }
        }
        return -1;
    }

    static int parseMajorVersion(@Nullable String constraint) {
        if (constraint == null) {
            return -1;
        }
        Matcher m = VERSION_PATTERN.matcher(constraint);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    static Measure bucketFor(int actualVersion, int targetVersion) {
        int lag = targetVersion - actualVersion;
        if (lag <= 0) {
            return Measure.Completed;
        }
        if (lag >= 10) {
            return Measure.Lag10PlusMajors;
        }
        if (lag >= 5) {
            return Measure.Lag5to9Majors;
        }
        if (lag >= 2) {
            return Measure.Lag2to4Majors;
        }
        return Measure.Lag1Major;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        int maxPossibleLag = majorVersion - 1;
        return Stream.of(Measure.values())
                .filter(measure -> measure.minimumLag <= maxPossibleLag)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        Lag10PlusMajors("10+ major versions behind",
                "Repository is 10 or more major versions behind the target.", 10),
        Lag5to9Majors("5-9 major versions behind",
                "Repository is between 5 and 9 major versions behind the target.", 5),
        Lag2to4Majors("2-4 major versions behind",
                "Repository is between 2 and 4 major versions behind the target.", 2),
        Lag1Major("1 major version behind",
                "Repository is one major version behind the target.", 1),
        Completed("Completed",
                "The upgrade to the desired Angular version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumLag;
    }
}
