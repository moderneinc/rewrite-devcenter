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

import io.moderne.devcenter.internal.PackageDependencies;
import io.moderne.devcenter.internal.VersionParts;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.javascript.marker.NodeResolutionResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class NodeVersionUpgrade extends UpgradeMigrationCard {

    @Option(displayName = "Major version",
            description = "The major version of Node.js to upgrade to.",
            example = "22")
    int majorVersion;

    @Option(displayName = "Upgrade recipe",
        description = "The recipe to use to upgrade.",
        example = "org.openrewrite.node.migrate.upgrade-node-22",
        required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Node.js version";

    @Override
    public String getInstanceName() {
        return "Move to Node.js " + majorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Node.js version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(NodeResolutionResult.class).ifPresent(result -> {
                        Map<String, String> engines = result.getEngines();
                        if (engines != null) {
                            String nodeConstraint = engines.get("node");
                            if (nodeConstraint != null) {
                                int actualVersion = parseMajorVersion(nodeConstraint);
                                if (actualVersion >= 0) {
                                    Measure measure = Measure.Completed;
                                    if (actualVersion < majorVersion) {
                                        if (actualVersion >= 24) {
                                            measure = Measure.Node24Plus;
                                        } else if (actualVersion >= 22) {
                                            measure = Measure.Node22Plus;
                                        } else if (actualVersion >= 20) {
                                            measure = Measure.Node20Plus;
                                        } else if (actualVersion >= 18) {
                                            measure = Measure.Node18Plus;
                                        } else if (actualVersion >= 16) {
                                            measure = Measure.Node16Plus;
                                        } else {
                                            measure = Measure.Node14Plus;
                                        }
                                    }

                                    upgradesAndMigrations.insertRow(ctx, NodeVersionUpgrade.this,
                                            measure, String.valueOf(actualVersion));
                                }
                            }
                        }
                    });
                }
                return tree;
            }
        };
    }

    /**
     * @return the major version of the lowest Node.js release the constraint admits, across those of its {@code ||}
     * alternatives that have a lower bound, or -1 when none has one, like {@code *} or {@code <=16}.
     */
    static int parseMajorVersion(String nodeConstraint) {
        int lowest = -1;
        for (String alternative : nodeConstraint.split("\\|\\|")) {
            String lowerBound = PackageDependencies.npmLowerBound(alternative.trim());
            if (lowerBound != null) {
                @Nullable Long[] parts = VersionParts.numeric(lowerBound);
                if (parts.length > 0 && parts[0] != null && (lowest < 0 || parts[0] < lowest)) {
                    lowest = parts[0].intValue();
                }
            }
        }
        return lowest;
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
        Node14Plus("Node.js 14+", "Technically, this is any version less than 16.", 14),
        Node16Plus("Node.js 16+", "Node.js 16 and later", 16),
        Node18Plus("Node.js 18+", "Node.js 18 and later", 18),
        Node20Plus("Node.js 20+", "Node.js 20 and later", 20),
        Node22Plus("Node.js 22+", "Node.js 22 and later", 22),
        Node24Plus("Node.js 24+", "Node.js 24 and later", 24),
        Completed("Completed", "The upgrade to the desired Node.js version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajorVersion;
    }
}
