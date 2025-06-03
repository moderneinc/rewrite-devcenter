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

import io.moderne.devcenter.internal.DataTableRowWatcher;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.search.ParentPomInsight;
import org.openrewrite.maven.table.ParentPomsInUse;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ParentPomUpgrade extends Recipe implements DevCenterMeasurer {
    transient UpgradesAndMigrations upgradesAndMigrations = new UpgradesAndMigrations(this);

    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card")
    String cardName;

    @Option(displayName = "Group identifier",
            description = "Group identifier used to match POM parents.",
            example = "org.springframework.boot")
    String groupIdPattern;

    @Option(displayName = "Artifact identifier",
            description = "Artifact identifier used to match POM parents.",
            example = "spring-boot-parent")
    String artifactIdPattern;

    @Option(displayName = "Target version",
            description = "The target version of the upgrade. " +
                          "Specify the version out to the desired patch version.",
            example = "3.4.5")
    String version;

    @Override
    public String getDisplayName() {
        return "Parent POM upgrade";
    }

    @Override
    public String getInstanceName() {
        return getCardName();
    }

    @Override
    public String getDescription() {
        return "Determine the current state of a repository relative to a desired parent POM upgrade.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton(DevCenter.DEVCENTER_TAG);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                ParentPomInsight parentPomInsight = new ParentPomInsight(groupIdPattern, artifactIdPattern, null, null);
                DataTableRowWatcher<ParentPomsInUse.Row> dataTableWatcher = new DataTableRowWatcher<>(parentPomInsight.getInUse(), ctx);
                dataTableWatcher.start();

                SemverRowBuilder rowBuilder = new SemverRowBuilder(cardName, version);
                Tree t = parentPomInsight.getVisitor().visitNonNull(tree, ctx);

                List<ParentPomsInUse.Row> parentPomsInUse = dataTableWatcher.stop();
                for (ParentPomsInUse.Row parentPomInUse : parentPomsInUse) {
                    if (parentPomInUse.getVersion() != null) {
                        UpgradesAndMigrations.Row row = rowBuilder.getRow(parentPomInUse.getVersion());
                        upgradesAndMigrations.insertRow(ctx, row);
                    }
                }

                return t;
            }
        });
    }

    public DevCenterMeasure[] getMeasures() {
        return SemverMeasure.values();
    }
}
