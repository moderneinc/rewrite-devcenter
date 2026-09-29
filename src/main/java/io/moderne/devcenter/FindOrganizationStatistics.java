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

import io.moderne.devcenter.internal.LineCounters;
import io.moderne.devcenter.table.OrganizationStatistics;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.binary.Binary;
import org.openrewrite.marker.Markup;
import org.openrewrite.quark.Quark;
import org.openrewrite.remote.Remote;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindOrganizationStatistics extends ScanningRecipe<AtomicLong> {
    transient OrganizationStatistics orgStats = new OrganizationStatistics(this);

    String displayName = "Find organization statistics";

    String description = "Counts lines of code per repository for organization-level statistics. Source files of a type without a line counter are not counted and are marked with a warning.";

    @Override
    public int maxCycles() {
        return 1;
    }

    @Override
    public AtomicLong getInitialValue(ExecutionContext ctx) {
        return new AtomicLong(0);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicLong acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(@Nullable Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof SourceFile && LineCounters.supports((SourceFile) tree)) {
                    acc.addAndGet(LineCounters.count((SourceFile) tree));
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicLong acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(@Nullable Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                // Quarks, binaries and recipe-generated remote files have no source text
                if (tree instanceof SourceFile && !(tree instanceof Quark) && !(tree instanceof Binary) &&
                    !(tree instanceof Remote) && !LineCounters.supports((SourceFile) tree)) {
                    return Markup.markup(tree, new Markup.Warn(Tree.randomId(),
                            "No line counter for " + tree.getClass().getName() + "; lines not counted", null));
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(AtomicLong acc, ExecutionContext ctx) {
        orgStats.insertRow(ctx, new OrganizationStatistics.Row(acc.get()));
        return emptyList();
    }
}
