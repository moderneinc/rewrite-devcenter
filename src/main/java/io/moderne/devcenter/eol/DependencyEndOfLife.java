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
import io.moderne.devcenter.eol.internal.ResolvedDependencyCoordinates;
import io.moderne.devcenter.eol.table.EndOfLifeReport;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.maven.search.FindMavenProject;

import java.util.Collection;
import java.util.List;

/**
 * A DevCenter card that reports the end-of-life status of an organization's <strong>JVM</strong>
 * (Maven and Gradle) dependencies against the {@link io.moderne.devcenter.eol.internal.EolFeed}.
 * Each dependency is matched by {@code pkg:maven/<groupId>}. See {@link AbstractEndOfLifeCard} for
 * the shared matching and aggregation behavior.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class DependencyEndOfLife extends AbstractEndOfLifeCard {

    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card.",
            example = "JVM dependency end of life")
    String cardName;

    @Option(displayName = "Approaching window (days)",
            description = "How many days before a release cycle's end-of-life date it should be " +
                          "reported as approaching end of life. Defaults to " + DEFAULT_APPROACHING_DAYS + ".",
            required = false,
            example = "180")
    @Nullable
    Integer approachingDays;

    @Option(displayName = "As-of date",
            description = "The reference date (ISO-8601, e.g. `2026-05-28`) used to evaluate " +
                          "end-of-life dates. Defaults to the current date when omitted.",
            required = false,
            example = "2026-05-28")
    @Nullable
    String asOf;

    @Option(displayName = "Feed",
            description = "Optional path, URL, or classpath resource of a Moderne EOL feed to use " +
                          "instead of the bundled snapshot. Lets an organization supply its own " +
                          "end-of-life schedules. See `FEED-FORMAT.md`.",
            required = false,
            example = "https://eol.internal.example.com/feed.yaml")
    @Nullable
    String feed;

    @Option(displayName = "Products",
            description = "Optional allow-list of feed product names to limit tracking to (e.g. " +
                          "`spring-boot`, `log4j`). When omitted, all products in the feed are tracked.",
            required = false,
            example = "spring-boot")
    @Nullable
    List<String> products;

    @Option(displayName = "Fix recipe",
            description = "Optional id of a recipe that remediates this card's findings, surfaced as " +
                          "a one-click fix in DevCenter (e.g. a dependency upgrade). When omitted, " +
                          "the card is report-only.",
            required = false,
            example = "org.openrewrite.java.dependencies.UpgradeDependencyVersion")
    @Nullable
    String fixRecipe;

    String displayName = "JVM dependency end of life";

    String description = "Determine the end-of-life status of an organization's Maven and Gradle " +
                         "dependencies, sourced from a Moderne EOL feed.";

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
        return fixRecipe;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> precondition() {
        return Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor());
    }

    @Override
    protected Collection<Detected> detect(SourceFile source) {
        return ResolvedDependencyCoordinates.find(source);
    }

    @Override
    protected EndOfLifeReport report() {
        return report;
    }
}
