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

import io.moderne.devcenter.table.OrganizationStatistics;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.binary.Binary;
import org.openrewrite.quark.Quark;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindOrganizationStatistics extends ScanningRecipe<AtomicLong> {
    transient OrganizationStatistics orgStats = new OrganizationStatistics(this);

    @Override
    public String getDisplayName() {
        return "Find organization statistics";
    }

    @Override
    public String getDescription() {
        return "Counts lines of code per repository for organization-level statistics.";
    }

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
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile && !(tree instanceof Quark) && !(tree instanceof Binary)) {
                    String printed = ((SourceFile) tree).printAll();
                    if (!printed.isEmpty()) {
                        long lines = printed.chars().filter(c -> c == '\n').count() +
                                     (printed.endsWith("\n") ? 0 : 1);
                        acc.addAndGet(lines);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(AtomicLong acc, ExecutionContext ctx) {
        orgStats.insertRow(ctx, new OrganizationStatistics.Row(acc.get()));
        return Collections.emptyList();
    }
}
