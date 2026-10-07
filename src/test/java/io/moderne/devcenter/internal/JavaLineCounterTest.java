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
package io.moderne.devcenter.internal;

import io.moderne.devcenter.internal.LineCountAssertions.MultilineMarker;
import org.junit.jupiter.api.Test;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.marker.CompactSourceFile;
import org.openrewrite.java.marker.OmitBraces;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import java.util.List;

import static io.moderne.devcenter.internal.LineCountAssertions.assertCountMatchesSource;
import static io.moderne.devcenter.internal.LineCountAssertions.assertMarkersNotCounted;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.openrewrite.Tree.randomId;

class JavaLineCounterTest {

    @Test
    void javaMatchesSource() {
        assertParsedCountsMatchSource(s -> JavaParser.fromJavaVersion().build().parse(s).findFirst(),
          J.CompilationUnit.class, JavaLineCounter::count, 1, 15,
          """
            package com.example;

            import java.util.List;

            /**
             * One.
             * Two.
             */
            class Test {
                String text = ""\"
                    line one
                    line two
                    ""\";

                /* multi
                   line */
                void method(List<String> list) {
                    // trailing
                }
            }
            """,
          "class A {\n    int x = 1;\n}",
          "class A {} // trailing"
        );
    }

    @Test
    void trailingCommaCountMatchesSource() {
        assertCountMatchesSource(parse(
          """
            enum Letters {
                A,
                B,
                ;
                @SuppressWarnings({
                    "unchecked",
                    "rawtypes",
                })
                int[] values() {
                    return new int[]{
                        1,
                        2,
                    };
                }
            }
            """
        ), JavaLineCounter::count);
    }

    @Test
    void compactSourceFileCountMatchesSource() {
        // Shaped like the Java 25 parser's implicit class, whose header repeats its first member's modifiers
        J.CompilationUnit cu = parse("class Test {private static\nfinal int X = 1;\n\nvoid main() {\n}\n}");
        J.ClassDeclaration c = cu.getClasses().getFirst();
        J.VariableDeclarations field = (J.VariableDeclarations) c.getBody().getStatements().getFirst();
        J.ClassDeclaration implicit = c.withPrefix(Space.EMPTY)
          .withMarkers(c.getMarkers().add(new CompactSourceFile(randomId())))
          .withModifiers(field.getModifiers())
          .withName(c.getName().withPrefix(Space.EMPTY))
          .withBody(c.getBody().withPrefix(Space.EMPTY).withMarkers(c.getBody().getMarkers().add(new OmitBraces(randomId()))));
        assertCountMatchesSource(cu.withClasses(List.of(implicit)), JavaLineCounter::count);
    }

    @Test
    void omittedSyntaxCountMatchesSource() {
        assertCountMatchesSource(parse(
          """
            record Point(int x, int y) {
                Point {
                    assert x >= 0;
                }
            }

            enum Op {
                PLUS {
                    int apply(int a) {
                        return a;
                    }
                };
                abstract int apply(int a);
            }
            """
        ), JavaLineCounter::count);
    }

    @Test
    void markersAreNotCounted() {
        J.CompilationUnit cu = parse(
          """
            class Test {
                int x = 1;
                void method(int a) {
                }
            }
            """
        );
        assertMarkersNotCounted(cu, SearchResult.found(cu, "multi\nline"));
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
                return Markup.warn(method, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(cu, 0));
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
                JContainer<Statement> params = method.getPadding().getParameters();
                return method.getPadding().withParameters(params.withMarkers(params.getMarkers().add(new MultilineMarker(randomId()))));
            }
        }.visitNonNull(cu, 0));
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                JLeftPadded<Expression> initializer = variable.getPadding().getInitializer();
                return initializer == null ? variable :
                  variable.getPadding().withInitializer(initializer.withMarkers(initializer.getMarkers().add(new MultilineMarker(randomId()))));
            }
        }.visitNonNull(cu, 0));
    }

    @Test
    void commentMarkersAreNotCounted() {
        J.CompilationUnit cu = parse("class A {\n    // c\n    int x;\n}\n");
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public Space visitSpace(Space space, Space.Location loc, Integer p) {
                return space.withComments(ListUtils.map(space.getComments(),
                  c -> c.withMarkers(c.getMarkers().add(new SearchResult(randomId(), "multi\nline")))));
            }
        }.visitNonNull(cu, 0));
    }

    @Test
    void javadocMarkersAreNotCounted() {
        J.CompilationUnit cu = parse("class A {\n    /**\n     * See {@link String}.\n     */\n    int x;\n}\n");
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier ident, Integer p) {
                return "String".equals(ident.getSimpleName()) ? SearchResult.found(ident, "multi\nline") : ident;
            }
        }.visitNonNull(cu, 0));
        assertMarkersNotCounted(cu, new JavaIsoVisitor<Integer>() {
            @Override
            public Space visitSpace(Space space, Space.Location loc, Integer p) {
                return space.withComments(ListUtils.map(space.getComments(), c -> c instanceof Javadoc j ?
                  (Comment) new JavadocVisitor<Integer>(new JavaVisitor<>()) {
                      @Override
                      public Javadoc visitReference(Javadoc.Reference reference, Integer p) {
                          return reference.withMarkers(reference.getMarkers().add(new MultilineMarker(randomId())));
                      }
                  }.visitNonNull(j, 0) : c));
            }
        }.visitNonNull(cu, 0));
    }

    private static J.CompilationUnit parse(String source) {
        return (J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(source).findFirst().orElseThrow();
    }
}
