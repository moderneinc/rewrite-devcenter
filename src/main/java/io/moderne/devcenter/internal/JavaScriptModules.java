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

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

public final class JavaScriptModules {

    private JavaScriptModules() {
    }

    /**
     * Whether the file is an ES module ({@code .mjs}/{@code .mts}, or a top-level {@code import}/{@code export})
     * or a CommonJS module ({@code .cjs}/{@code .cts}, or a {@code require("…")} call or an assignment to
     * {@code module.exports}/{@code exports} outside any function).
     * <p>
     * Mirrors {@code isModule} in recipes-javascript's {@code module-kind.ts}, which decides where {@code NoVar}
     * may change a top-level {@code var}, so keep the two in sync.
     */
    public static boolean isModule(JS.CompilationUnit cu) {
        String path = cu.getSourcePath().toString();
        if (path.matches(".*\\.[mc][jt]s")) {
            return true;
        }
        for (Statement statement : cu.getStatements()) {
            if (isEsModuleStatement(statement)) {
                return true;
            }
        }
        AtomicBoolean commonJs = new AtomicBoolean();
        new CommonJsFinder().visit(cu, commonJs);
        return commonJs.get();
    }

    private static boolean isEsModuleStatement(Statement statement) {
        if (statement instanceof JS.Import || statement instanceof JS.ExportDeclaration ||
            statement instanceof JS.ExportAssignment) {
            return true;
        }
        for (J.Modifier modifier : modifiersOf(statement)) {
            if ("export".equals(modifier.getKeyword())) {
                return true;
            }
        }
        return false;
    }

    private static List<J.Modifier> modifiersOf(Statement statement) {
        if (statement instanceof J.VariableDeclarations) {
            return ((J.VariableDeclarations) statement).getModifiers();
        }
        if (statement instanceof JS.ScopedVariableDeclarations) {
            return ((JS.ScopedVariableDeclarations) statement).getModifiers();
        }
        if (statement instanceof J.MethodDeclaration) {
            return ((J.MethodDeclaration) statement).getModifiers();
        }
        if (statement instanceof J.ClassDeclaration) {
            return ((J.ClassDeclaration) statement).getModifiers();
        }
        if (statement instanceof JS.TypeDeclaration) {
            return ((JS.TypeDeclaration) statement).getModifiers();
        }
        if (statement instanceof JS.NamespaceDeclaration) {
            return ((JS.NamespaceDeclaration) statement).getModifiers();
        }
        return emptyList();
    }

    private static class CommonJsFinder extends JavaScriptIsoVisitor<AtomicBoolean> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
            if (isRequireCall(method)) {
                found.set(true);
                return method;
            }
            return found.get() ? method : super.visitMethodInvocation(method, found);
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean found) {
            if (isExportsTarget(assignment.getVariable())) {
                found.set(true);
                return assignment;
            }
            return found.get() ? assignment : super.visitAssignment(assignment, found);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean found) {
            return method;
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
            return lambda;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
            return classDecl;
        }

        @Override
        public JS.ArrowFunction visitArrowFunction(JS.ArrowFunction arrowFunction, AtomicBoolean found) {
            return arrowFunction;
        }

        @Override
        public JS.ComputedPropertyMethodDeclaration visitComputedPropertyMethodDeclaration(JS.ComputedPropertyMethodDeclaration method, AtomicBoolean found) {
            return method;
        }

        private static boolean isRequireCall(J.MethodInvocation method) {
            if (method.getSelect() != null || !"require".equals(method.getSimpleName())) {
                return false;
            }
            List<Expression> arguments = method.getArguments();
            return arguments.size() == 1 && arguments.get(0) instanceof J.Literal &&
                   ((J.Literal) arguments.get(0)).getValue() instanceof String;
        }

        private static boolean isExportsTarget(Expression variable) {
            Expression node = variable;
            while (node instanceof J.FieldAccess || node instanceof J.ArrayAccess) {
                Expression target = node instanceof J.FieldAccess ?
                        ((J.FieldAccess) node).getTarget() :
                        ((J.ArrayAccess) node).getIndexed();
                if (target instanceof J.Identifier) {
                    String name = ((J.Identifier) target).getSimpleName();
                    if ("exports".equals(name) ||
                        ("module".equals(name) && node instanceof J.FieldAccess &&
                         "exports".equals(((J.FieldAccess) node).getSimpleName()))) {
                        return true;
                    }
                }
                node = target;
            }
            return false;
        }
    }
}
