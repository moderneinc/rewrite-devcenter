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

import io.moderne.devcenter.DevCenterMeasure;
import io.moderne.devcenter.UpgradeMigrationCard;
import io.moderne.devcenter.eol.internal.Detected;
import io.moderne.devcenter.eol.internal.EolFeed;
import io.moderne.devcenter.eol.internal.FeedLoader;
import io.moderne.devcenter.eol.table.EndOfLifeReport;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared behavior for every end-of-life DevCenter card: gate on the relevant source files, detect
 * each card's dependencies/runtimes as {@link Detected} coordinates, match them against the
 * (bundled or org-supplied) {@link EolFeed}, and record a per-repository worst {@link EolStatus}
 * plus a per-item {@link EndOfLifeReport} row.
 * <p>
 * Concrete cards declare their own {@code @Option} fields (OpenRewrite only discovers options on
 * the recipe's own class) and expose them through the abstract accessors below.
 */
public abstract class AbstractEndOfLifeCard extends UpgradeMigrationCard {

    /**
     * Default window, in days, before a release cycle's end-of-life date during which it is
     * reported as {@link EolStatus#EndOfLifeApproaching}.
     */
    public static final int DEFAULT_APPROACHING_DAYS = 180;

    // ExecutionContext key under which the per-run detail-report dedup set is kept. The context is
    // scoped to a single recipe execution (i.e. per repository in a Moderne org run), so this dedups
    // the per-source-file duplication within a repository (e.g. a runtime detected on every .java
    // file) WITHOUT collapsing identical findings across repositories. It deliberately is NOT a
    // recipe-instance field: a Recipe is shared across the whole run and must not carry mutable state.
    private static final String REPORTED = AbstractEndOfLifeCard.class.getName() + ".reported";

    protected abstract @Nullable Integer approachingDays();

    protected abstract @Nullable String asOf();

    protected abstract @Nullable String feed();

    protected abstract @Nullable List<String> products();

    /** Limits the visitor to the source files this card understands. */
    protected abstract TreeVisitor<?, ExecutionContext> precondition();

    /** Extracts the dependencies/runtimes this card tracks from a single source file. */
    protected abstract Collection<Detected> detect(SourceFile source);

    /** The card's per-item detail report instance. */
    protected abstract EndOfLifeReport report();

    /**
     * Recipe id offered as the card's one-click fix in DevCenter, or {@code null} for a report-only
     * card. Concrete cards return their {@code fixRecipe} option (optionally with a default).
     */
    protected abstract @Nullable String fixRecipe();

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(EolStatus.values());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return fixRecipe();
    }

    @Override
    public Validated<Object> validate(ExecutionContext ctx) {
        Validated<Object> validated = super.validate(ctx);
        if (asOf() != null) {
            validated = validated.and(Validated.test(
                    "asOf",
                    "must be an ISO-8601 date such as 2026-05-28",
                    asOf(),
                    v -> {
                        try {
                            LocalDate.parse(v);
                            return true;
                        } catch (DateTimeParseException e) {
                            return false;
                        }
                    }));
        }
        return validated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(precondition(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof SourceFile) {
                    process((SourceFile) tree, ctx);
                }
                return tree;
            }
        });
    }

    private void process(SourceFile source, ExecutionContext ctx) {
        LocalDate referenceDate = asOf() == null ? LocalDate.now() : LocalDate.parse(asOf());
        int window = approachingDays() == null ? DEFAULT_APPROACHING_DAYS : approachingDays();
        List<String> allowed = products();
        EolFeed feed = FeedLoader.load(feed());
        Set<String> reported = ctx.computeMessageIfAbsent(REPORTED, k -> ConcurrentHashMap.newKeySet());
        for (Detected d : detect(source)) {
            Optional<EolFeed.Match> matched = feed.match(d.getPurl(), d.getVersion());
            if (!matched.isPresent()) {
                continue;
            }
            EolFeed.Match match = matched.get();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(match.getProductSlug())) {
                continue;
            }
            EolStatus status = EolStatus.of(match.getEolFrom(), match.isEol(), referenceDate, window);
            // Always feed the dashboard table (it keeps the worst measure per repo); dedup only the
            // detail report rows.
            upgradesAndMigrations.insertRow(ctx, this, status, d.getVersion());
            // Namespace by card instance so the per-runtime cards (which share one ExecutionContext)
            // dedup independently of one another.
            String key = getInstanceName() + '|' + d.getPurl().key() + '@' + d.getVersion() + '#' + status.name();
            if (reported.add(key)) {
                report().insertRow(ctx, new EndOfLifeReport.Row(
                        d.getEcosystem(),
                        d.getKind().getLabel(),
                        d.getDisplayName(),
                        d.getVersion(),
                        match.getProductSlug(),
                        match.getCycle(),
                        match.getEolFrom() == null ? "" : match.getEolFrom().toString(),
                        status.name()));
            }
        }
    }
}
