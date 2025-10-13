package io.moderne.devcenter;

import io.moderne.devcenter.internal.DataTableRowWatcher;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.java.dependencies.DependencyVulnerabilityCheck;
import org.openrewrite.java.dependencies.DependencyVulnerabilityCheckBase;
import org.openrewrite.java.dependencies.DependencyVulnerabilityCheckBase.UpgradeDelta;
import org.openrewrite.java.dependencies.internal.Version;
import org.openrewrite.java.dependencies.internal.VersionParser;
import org.openrewrite.java.dependencies.table.VulnerabilityReport;
import org.openrewrite.maven.search.FindMavenProject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Value
@EqualsAndHashCode(callSuper = false)
public class FixVulnerableDependencies extends UpgradeMigrationCard {

    @Override
    public String getDisplayName() {
        return "Fixable Vulnerabilities status";
    }

    @Override
    public String getInstanceName() {
        return "Fixable vulnerable dependencies";
    }

    @Override
    public String getDescription() {
        return "Determine the current state of a repository relative to its vulnerabilities.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        DependencyVulnerabilityCheck scan = new DependencyVulnerabilityCheck("runtime", true, UpgradeDelta.major);
        AtomicReference<DependencyVulnerabilityCheckBase.Accumulator> accumulator = new AtomicReference<>();
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()),
                new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree preVisit(Tree tree, ExecutionContext ctx) {
                        stopAfterPreVisit();
                        if (accumulator.get() == null) {
                            accumulator.set(scan.getInitialValue(ctx));
                        }
                        scan.getScanner(accumulator.get()).visitNonNull(tree, ctx);

                        DataTableRowWatcher<VulnerabilityReport.Row> dataTableWatcher = new DataTableRowWatcher<>(
                                scan.getVulnerabilityReport(), ctx);
                        dataTableWatcher.start();
                        scan.generate(accumulator.get(), ctx);

                        List<VulnerabilityReport.Row> vulnerableDependencies = dataTableWatcher.stop();

                        if (vulnerableDependencies.isEmpty()) {
                            upgradesAndMigrations.insertRow(ctx, FixVulnerableDependencies.this,
                                    UpgradeDeltaMeasure.Completed, "No vulnerabilities found");
                        }

                        for (VulnerabilityReport.Row row : vulnerableDependencies) {
                            if (row.getFixedVersion() == null) {
                                upgradesAndMigrations.insertRow(ctx, FixVulnerableDependencies.this,
                                        UpgradeDeltaMeasure.Unfixable, "Only vulnerabilities are not fixable.");
                                continue;
                            }
                            upgradesAndMigrations.insertRow(ctx, FixVulnerableDependencies.this,
                                    UpgradeDeltaMeasure.ofVersions(row.getVersion(), row.getFixedVersion()),
                                    "Fixable vulnerabilities found.");
                        }

                        return tree;
                    }
                });
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return "org.openrewrite.java.dependencies.DependencyVulnerabilityCheck";
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(UpgradeDeltaMeasure.values());
    }

    @RequiredArgsConstructor
    @Getter
    public enum UpgradeDeltaMeasure implements DevCenterMeasure {
        Major("Vulnerabilities that require a major version bump found."),
        Minor("Vulnerabilities that require a minor version bump found."),
        Patch("Vulnerabilities that require a patch found."),
        Unfixable("Vulnerabilities that cannot be fixed found."),
        Completed("No fixable vulnerabilities found.");

        private static final VersionParser parser = new VersionParser();

        @Override
        public @Language("markdown") String getName() {
            return name();
        }

        private final @Language("markdown") String description;

        public static UpgradeDeltaMeasure ofVersions(String currentVersion, String newVersion) {
            Version currentParsed = parser.transform(currentVersion);
            Version newParsed = parser.transform(newVersion);
            Long[] currentNumericParts = currentParsed.getNumericParts();
            Long[] newNumericParts = newParsed.getNumericParts();
            int maxLength = Math.max(currentNumericParts.length, newNumericParts.length);
            for (int i = 0; i < maxLength; i++) {
                long currentPart = i < currentNumericParts.length ? currentNumericParts[i] : 0L;
                long newPart = i < newNumericParts.length ? newNumericParts[i] : 0L;
                if (currentPart != newPart) {
                    switch (i) {
                        case 0: return UpgradeDeltaMeasure.Major;
                        case 1: return UpgradeDeltaMeasure.Minor;
                        default: return UpgradeDeltaMeasure.Patch;
                    }
                }
            }
            return Major;
        }
    }
}