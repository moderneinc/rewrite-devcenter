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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.golang.tree.Go;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.python.tree.Py;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class JUnitJupiterUpgrade extends UpgradeMigrationCard {
    // org.junit.Test is @Target(METHOD), so unlike Jupiter's @Test it can't be a meta-annotation
    private static final AnnotationMatcher JUNIT4_TEST = new AnnotationMatcher("@org.junit.Test", false);
    private static final AnnotationMatcher JUPITER_TEST = new AnnotationMatcher("@org.junit.jupiter.api.Test", true);
    private static final String JUPITER_6_BY_SOURCE_SET = JUnitJupiterUpgrade.class.getName() + ".jupiter6BySourceSet";

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            example = "org.openrewrite.java.testing.junit5.JUnit4to5Migration",
            required = false)
    @Nullable
    String upgradeRecipe;

    @Getter
    final String displayName = "Move to JUnit 6";

    @Getter
    final String description = "Move to JUnit Jupiter.";

    @Override
    public String getInstanceName() {
        return displayName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // Adapting a Java visitor to these languages is costly, and their tests can't use JUnit
                return sourceFile instanceof JavaSourceFile &&
                       !(sourceFile instanceof JS.CompilationUnit || sourceFile instanceof Py.CompilationUnit ||
                         sourceFile instanceof Cs.CompilationUnit || sourceFile instanceof Go.CompilationUnit);
            }

            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                // The best row is the lowest ordinal, and all rows of an ordinal are equal, so only
                // look for measures that could still improve on what this repository already reported
                int best = upgradesAndMigrations.bestOrdinal(ctx, getInstanceName());
                if (best <= Measure.JUnit4.ordinal()) {
                    return tree;
                }

                JavaSourceFile cu = (JavaSourceFile) tree;
                if (containsAnnotation(cu, JUNIT4_TEST)) {
                    upgradesAndMigrations.insertRow(ctx, JUnitJupiterUpgrade.this, Measure.JUnit4, "JUnit 4");
                    return tree;
                }
                if (best <= Measure.JUnit5.ordinal()) {
                    return tree;
                }

                if (best <= Measure.Completed.ordinal() && isOnJupiter6(cu, ctx)) {
                    return tree;
                }
                if (containsAnnotation(cu, JUPITER_TEST)) {
                    if (isOnJupiter6(cu, ctx)) {
                        upgradesAndMigrations.insertRow(ctx, JUnitJupiterUpgrade.this, Measure.Completed, "JUnit 6");
                    } else {
                        upgradesAndMigrations.insertRow(ctx, JUnitJupiterUpgrade.this, Measure.JUnit5, "JUnit 5");
                    }
                }
                return tree;
            }
        };
    }

    private static boolean containsAnnotation(JavaSourceFile cu, AnnotationMatcher matcher) {
        if (!usesMatchingType(cu, matcher)) {
            return false;
        }
        AtomicBoolean found = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J preVisit(J tree, AtomicBoolean found) {
                if (found.get()) {
                    stopAfterPreVisit();
                }
                return tree;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean found) {
                if (matcher.matches(annotation)) {
                    found.set(true);
                    return annotation;
                }
                return super.visitAnnotation(annotation, found);
            }
        }.visit(cu, found);
        return found.get();
    }

    private static boolean usesMatchingType(JavaSourceFile cu, AnnotationMatcher matcher) {
        for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
            if (matcher.matchesAnnotationOrMetaAnnotation(TypeUtils.asFullyQualified(type))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnJupiter6(JavaSourceFile cu, ExecutionContext ctx) {
        Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
        if (!sourceSet.isPresent()) {
            return false;
        }
        // Every file of a source set shares its classpath, so scan its GAVs once per run
        Map<UUID, Boolean> jupiter6BySourceSet = ctx.computeMessageIfAbsent(JUPITER_6_BY_SOURCE_SET, k -> new ConcurrentHashMap<>());
        return jupiter6BySourceSet.computeIfAbsent(sourceSet.get().getId(), id -> sourceSet.get().getGavToTypes().keySet().stream()
                .anyMatch(gav -> gav.startsWith("org.junit.jupiter:junit-jupiter-api:6")));
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(Measure.values());
    }

    @Override
    public String getFixRecipeId() {
        return upgradeRecipe == null ?
                "org.openrewrite.java.testing.junit6.JUnit5to6Migration" :
                upgradeRecipe;
    }

    @RequiredArgsConstructor
    @Getter
    public enum Measure implements DevCenterMeasure {
// TODO Can we add JUnit 3 without affecting already stored data?
//        JUnit3("JUnit 3", "On JUnit 3 or less. Specifically looks for `junit.framework.TestCase`."),
        JUnit4("JUnit 4", "On JUnit 4 or less. Specifically looks for `@org.junit.Test`."),
        JUnit5("JUnit 5", "On JUnit Jupiter 5."),
        Completed("Completed", "On JUnit Jupiter 6.");

        private final @Language("markdown") String name;

        private final @Language("markdown") String description;
    }
}
