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

import io.moderne.devcenter.internal.JavaScriptModules;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.search.FindVendoredOrBundled;
import org.openrewrite.javascript.tree.JS;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Value
@EqualsAndHashCode(callSuper = false)
public class EcmaScriptModernization extends UpgradeMigrationCard {

    private static final String DECLARATIONS = EcmaScriptModernization.class.getName() + ".declarations";

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            example = "org.openrewrite.javascript.migrate.es6.ModernizeToES6",
            required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to ECMAScript 6";

    String description = "Determine how many of a repository's JavaScript and TypeScript variable declarations " +
                         "still use `var` rather than `let` or `const`. A `var` that can't become `let` without " +
                         "changing behavior, such as a top-level one in a classic script or an exported or ambient " +
                         "one, isn't counted. Vendored, bundled and build-output files are skipped.";

    @Override
    public String getInstanceName() {
        return displayName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new FindVendoredOrBundled().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof JS.CompilationUnit &&
                       !sourceFile.getSourcePath().toString().matches(".*\\.d\\.[mc]?ts");
            }

            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                JS.CompilationUnit cu = (JS.CompilationUnit) tree;
                new DeclarationCounter(JavaScriptModules.isModule(cu))
                        .visit(cu, ctx.computeMessageIfAbsent(DECLARATIONS, k -> new Declarations()));
                return tree;
            }
        });
    }

    @Override
    public void onComplete(ExecutionContext ctx) {
        Declarations declarations = ctx.getMessage(DECLARATIONS);
        if (declarations != null && declarations.total.get() > 0) {
            long var = declarations.var.get();
            long total = declarations.total.get();
            upgradesAndMigrations.insertRow(ctx, this, Measure.of(var, total), Math.round(100.0 * var / total) + "%");
        }
        super.onComplete(ctx);
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(Measure.values());
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        MostlyVar("Mostly `var`", "More than half of the variable declarations use `var`."),
        PartlyVar("Partly `var`", "Between 10% and 50% of the variable declarations use `var`."),
        LittleVar("Little `var`", "Fewer than 10% of the variable declarations use `var`."),
        Completed("Completed", "No variable declarations use `var`.");

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;

        static Measure of(long var, long total) {
            if (var == 0) {
                return Completed;
            }
            if (var * 2 > total) {
                return MostlyVar;
            }
            if (var * 10 >= total) {
                return PartlyVar;
            }
            return LittleVar;
        }
    }

    private static class Declarations {
        final AtomicLong var = new AtomicLong();
        final AtomicLong total = new AtomicLong();
    }

    @RequiredArgsConstructor
    private static class DeclarationCounter extends JavaScriptIsoVisitor<Declarations> {
        private final boolean module;

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, Declarations declarations) {
            count(multiVariable.getModifiers(), declarations);
            return super.visitVariableDeclarations(multiVariable, declarations);
        }

        @Override
        public JS.ScopedVariableDeclarations visitScopedVariableDeclarations(JS.ScopedVariableDeclarations scopedVariableDeclarations, Declarations declarations) {
            count(scopedVariableDeclarations.getModifiers(), declarations);
            return super.visitScopedVariableDeclarations(scopedVariableDeclarations, declarations);
        }

        private void count(List<J.Modifier> modifiers, Declarations declarations) {
            String keyword = null;
            boolean exportedOrAmbient = false;
            for (J.Modifier modifier : modifiers) {
                String k = modifier.getKeyword();
                if ("var".equals(k) || "let".equals(k) || "const".equals(k)) {
                    keyword = k;
                } else if ("export".equals(k) || "declare".equals(k) || "default".equals(k)) {
                    exportedOrAmbient = true;
                }
            }
            if (keyword == null) {
                return;
            }
            if ("var".equals(keyword)) {
                Scope scope = enclosingScope();
                if (exportedOrAmbient || scope == Scope.Ambient || (scope == Scope.TopLevel && !module)) {
                    return;
                }
                declarations.var.incrementAndGet();
            }
            declarations.total.incrementAndGet();
        }

        private Scope enclosingScope() {
            Scope scope = Scope.TopLevel;
            for (Iterator<Object> path = getCursor().getPath(); path.hasNext(); ) {
                Object value = path.next();
                if (value instanceof JS.NamespaceDeclaration &&
                    ((JS.NamespaceDeclaration) value).getModifiers().stream().anyMatch(m -> "declare".equals(m.getKeyword()))) {
                    return Scope.Ambient;
                }
                if (value instanceof J.MethodDeclaration || value instanceof J.Lambda ||
                    value instanceof JS.ArrowFunction || value instanceof JS.ComputedPropertyMethodDeclaration ||
                    value instanceof JS.NamespaceDeclaration || (value instanceof J.Block && ((J.Block) value).isStatic())) {
                    scope = Scope.Function;
                }
            }
            return scope;
        }

        private enum Scope {
            TopLevel,
            Function,
            Ambient
        }
    }
}
