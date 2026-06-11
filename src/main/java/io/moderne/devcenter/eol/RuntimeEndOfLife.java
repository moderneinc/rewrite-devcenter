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
package io.moderne.devcenter.eol;

import io.moderne.devcenter.eol.internal.Detected;
import io.moderne.devcenter.eol.internal.JavaRuntime;
import io.moderne.devcenter.eol.internal.NpmManifest;
import io.moderne.devcenter.eol.internal.NuGetProject;
import io.moderne.devcenter.eol.internal.PythonRuntime;
import io.moderne.devcenter.eol.table.EndOfLifeReport;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A DevCenter card that reports the end-of-life status of an organization's <strong>language
 * runtimes</strong>: the Java version (from the {@code JavaVersion} marker), the Node.js version
 * (from {@code engines.node} in {@code package.json}), the .NET runtime (from the
 * {@code <TargetFramework(s)>} moniker in {@code *.csproj}), and Python (from {@code .python-version},
 * {@code runtime.txt}, or {@code pyproject.toml}).
 * <p>
 * Set the {@code runtime} option to track a single runtime — each per-runtime card then scans and
 * detects only that ecosystem's files (so the runtime is in the card title and there's no wasted
 * cross-ecosystem scanning). When {@code runtime} is omitted the card tracks every runtime at once.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RuntimeEndOfLife extends AbstractEndOfLifeCard {

    // Default fixes for the obvious runtime cards (upgrade to the current major); both overridable
    // via the fixRecipe option.
    //
    // MAINTENANCE: these must track the current Java LTS and .NET major. When a new Java LTS or .NET
    // release ships, bump both constants AND the defaults table in README.md ("Fix actions") in the
    // same change so the docs and behavior stay in sync.
    private static final String DEFAULT_JAVA_FIX = "org.openrewrite.java.migrate.UpgradeToJava25";
    private static final String DEFAULT_DOTNET_FIX =
            "OpenRewrite.Recipes.CSharp.Migration.Dotnet.Net10.UpgradeToDotNet10";

    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card.",
            example = "Runtime end of life")
    String cardName;

    @Option(displayName = "Approaching window (days)",
            description = "How many days before a release cycle's end-of-life date it should be " +
                          "reported as approaching end of life. Defaults to " + DEFAULT_APPROACHING_DAYS + ".",
            required = false,
            example = "180")
    @Nullable
    Integer approachingDays;

    @Option(displayName = "As-of date",
            description = "The reference date (ISO-8601) used to evaluate end-of-life dates. " +
                          "Defaults to the current date when omitted.",
            required = false,
            example = "2026-05-28")
    @Nullable
    String asOf;

    @Option(displayName = "Feed",
            description = "Optional path, URL, or classpath resource of a Moderne EOL feed to use " +
                          "instead of the bundled snapshot. See `FEED-FORMAT.md`.",
            required = false,
            example = "https://eol.internal.example.com/feed.yaml")
    @Nullable
    String feed;

    @Option(displayName = "Products",
            description = "Optional allow-list of feed product names to limit tracking to (e.g. " +
                          "`openjdk`, `nodejs`, `dotnet`). When omitted, all runtimes in the feed " +
                          "are tracked.",
            required = false,
            example = "openjdk")
    @Nullable
    List<String> products;

    @Option(displayName = "Runtime",
            description = "The single language runtime to track (`Java`, `NodeJs`, `DotNet`, or " +
                          "`Python`). When set, only that ecosystem's files are scanned. When " +
                          "omitted, every runtime is tracked in one card.",
            required = false,
            example = "DotNet")
    @Nullable
    RuntimeType runtime;

    @Option(displayName = "Fix recipe",
            description = "Optional id of a migration recipe offered as a one-click fix in DevCenter. " +
                          "Defaults to a Java upgrade for the `Java` runtime; provide your own for " +
                          "other runtimes (e.g. a .NET upgrade for `DotNet`). When omitted and no " +
                          "default applies, the card is report-only.",
            required = false,
            example = "org.openrewrite.java.migrate.UpgradeToJava25")
    @Nullable
    String fixRecipe;

    String displayName = "Runtime end of life";

    String description = "Determine the end-of-life status of an organization's language runtimes " +
                         "(Java, Node.js, .NET), sourced from a Moderne EOL feed.";

    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    transient EndOfLifeReport report = new EndOfLifeReport(this);

    @Override
    public String getInstanceName() {
        return cardName;
    }

    @Override
    protected @Nullable Integer approachingDays() {
        return approachingDays;
    }

    @Override
    protected @Nullable String asOf() {
        return asOf;
    }

    @Override
    protected @Nullable String feed() {
        return feed;
    }

    @Override
    protected @Nullable List<String> products() {
        return products;
    }

    @Override
    protected @Nullable String fixRecipe() {
        if (fixRecipe != null) {
            return fixRecipe;
        }
        // Default the obvious runtime upgrades; other runtimes are report-only unless a fix is given.
        if (runtime == RuntimeType.Java) {
            return DEFAULT_JAVA_FIX;
        }
        return runtime == RuntimeType.DotNet ? DEFAULT_DOTNET_FIX : null;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> precondition() {
        if (runtime == null) {
            return Preconditions.or(javaFiles(), nodeFiles(), dotNetFiles(), pythonFiles());
        }
        switch (runtime) {
            case Java:
                return javaFiles();
            case NodeJs:
                return nodeFiles();
            case DotNet:
                return dotNetFiles();
            default:
                return pythonFiles();
        }
    }

    @Override
    protected Collection<Detected> detect(SourceFile source) {
        Set<Detected> runtimes = new LinkedHashSet<>();
        if (runtime == null || runtime == RuntimeType.Java) {
            runtimes.addAll(JavaRuntime.find(source));
        }
        if (runtime == null || runtime == RuntimeType.NodeJs) {
            runtimes.addAll(NpmManifest.runtimes(source));
        }
        if (runtime == null || runtime == RuntimeType.DotNet) {
            runtimes.addAll(NuGetProject.runtimes(source));
        }
        if (runtime == null || runtime == RuntimeType.Python) {
            runtimes.addAll(PythonRuntime.find(source));
        }
        return runtimes;
    }

    private static TreeVisitor<?, ExecutionContext> javaFiles() {
        return new FindSourceFiles("**/*.java").getVisitor();
    }

    private static TreeVisitor<?, ExecutionContext> nodeFiles() {
        return new FindSourceFiles("**/package.json").getVisitor();
    }

    private static TreeVisitor<?, ExecutionContext> dotNetFiles() {
        return new FindSourceFiles("**/*.csproj").getVisitor();
    }

    private static TreeVisitor<?, ExecutionContext> pythonFiles() {
        return Preconditions.or(
                new FindSourceFiles("**/.python-version").getVisitor(),
                new FindSourceFiles("**/runtime.txt").getVisitor(),
                new FindSourceFiles("**/pyproject.toml").getVisitor());
    }

    @Override
    protected EndOfLifeReport report() {
        return report;
    }
}
