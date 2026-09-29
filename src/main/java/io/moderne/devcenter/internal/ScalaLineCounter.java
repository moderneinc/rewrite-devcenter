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

import io.moderne.devcenter.internal.Newlines.Counter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.marker.ImplicitReturn;
import org.openrewrite.java.marker.OmitParentheses;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.scala.ScalaVisitor;
import org.openrewrite.scala.marker.AsInstanceOfPrefix;
import org.openrewrite.scala.marker.BlockArgument;
import org.openrewrite.scala.marker.Curried;
import org.openrewrite.scala.marker.ExtraConstructorParamLists;
import org.openrewrite.scala.marker.FunctionApplication;
import org.openrewrite.scala.marker.Implicit;
import org.openrewrite.scala.marker.IndentedSyntax;
import org.openrewrite.scala.marker.InfixNotation;
import org.openrewrite.scala.marker.InfixTypeNotation;
import org.openrewrite.scala.marker.LambdaParameter;
import org.openrewrite.scala.marker.MethodBodyEqualsPrefix;
import org.openrewrite.scala.marker.OmitBraces;
import org.openrewrite.scala.marker.OmitName;
import org.openrewrite.scala.marker.PartialFunctionLiteral;
import org.openrewrite.scala.marker.ReturnTypeColonPrefix;
import org.openrewrite.scala.marker.RightAssociative;
import org.openrewrite.scala.marker.SObject;
import org.openrewrite.scala.marker.ScalaForLoop;
import org.openrewrite.scala.marker.TrailingComma;
import org.openrewrite.scala.marker.TypeAscriptionColonPrefix;
import org.openrewrite.scala.marker.TypeProjection;
import org.openrewrite.scala.marker.UnderscorePlaceholderLambda;
import org.openrewrite.scala.marker.ValVarKeyword;
import org.openrewrite.scala.tree.S;

import java.util.List;

/**
 * Counts lines in a Scala LST in-process. ScalaPrinter skips, repeats or rebuilds much of what a plain
 * visitor walks, so every method it overrides is mirrored here, visiting exactly the spaces and raw text it prints.
 */
final class ScalaLineCounter extends ScalaVisitor<Counter> {

    private ScalaLineCounter() {
    }

    static long count(S.CompilationUnit cu) {
        Counter c = new Counter();
        new ScalaLineCounter().visit(cu, c);
        Space eof = cu.getEof();
        if (!eof.getWhitespace().isEmpty() || !eof.getComments().isEmpty()) {
            return Newlines.lineCount(c, eof);
        }
        List<Statement> statements = cu.getStatements();
        return Newlines.lineCount(c, !statements.isEmpty() && endsWithNewline(statements.get(statements.size() - 1)));
    }

    // With an empty EOF, source text kept verbatim by the parser may hold the final newline
    private static boolean endsWithNewline(J tree) {
        String text = null;
        if (tree instanceof J.Unknown) {
            text = ((J.Unknown) tree).getSource().getText();
        } else if (tree instanceof S.PatternDefinition) {
            text = ((S.PatternDefinition) tree).getText();
        } else if (tree instanceof S.TypeAlias) {
            text = ((S.TypeAlias) tree).getText();
        } else if (tree instanceof S.XmlLiteral) {
            text = ((S.XmlLiteral) tree).getSource();
        }
        return text != null && text.endsWith("\n");
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, Counter c) {
        Newlines.addSpace(c, space);
        return space;
    }

    private void space(@Nullable Space space, Counter c) {
        if (space != null) {
            Newlines.addSpace(c, space);
        }
    }

    // Marker text is printed only by the marker printer, except the ones mirrored explicitly below
    @Override
    public Markers visitMarkers(@Nullable Markers markers, Counter c) {
        return markers == null ? Markers.EMPTY : markers;
    }

    @Override
    public <M extends Marker> M visitMarker(Marker marker, Counter c) {
        //noinspection unchecked
        return (M) marker;
    }

    @Override
    public J visitLiteral(J.Literal literal, Counter c) {
        Newlines.addText(c, literal.getValueSource());
        return super.visitLiteral(literal, c);
    }

    @Override
    public J visitIdentifier(J.Identifier ident, Counter c) {
        Newlines.addText(c, ident.getSimpleName());
        return super.visitIdentifier(ident, c);
    }

    @Override
    public J visitUnknownSource(J.Unknown.Source source, Counter c) {
        Newlines.addText(c, source.getText());
        return super.visitUnknownSource(source, c);
    }

    @Override
    public J visitErroneous(J.Erroneous erroneous, Counter c) {
        Newlines.addText(c, erroneous.getText());
        return super.visitErroneous(erroneous, c);
    }

    @Override
    public J visitModifier(J.Modifier mod, Counter c) {
        if (mod.getKeyword() != null && (mod.getType() == J.Modifier.Type.LanguageExtension ||
                                         (mod.getType() == J.Modifier.Type.Private || mod.getType() == J.Modifier.Type.Protected) &&
                                         mod.getKeyword().contains("["))) {
            Newlines.addText(c, mod.getKeyword());
        }
        return super.visitModifier(mod, c);
    }

    @Override
    public <T> @Nullable JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right, JRightPadded.Location loc, Counter c) {
        if (right != null) {
            right.getMarkers().findFirst(TrailingComma.class).ifPresent(tc -> space(tc.getPrefix(), c));
        }
        return super.visitRightPadded(right, loc, c);
    }

    // Mirrors ScalaPrinter.visitListElementSuffix
    private void listElementSuffix(JRightPadded<? extends J> node, boolean isLast, Counter c) {
        if (isLast) {
            node.getMarkers().findFirst(TrailingComma.class).ifPresent(tc -> space(tc.getPrefix(), c));
        }
        space(node.getAfter(), c);
    }

    // Mirrors JavaPrinter.visitRightPadded for a single element
    private void rightPadded(@Nullable JRightPadded<? extends J> right, Counter c) {
        if (right != null) {
            visit(right.getElement(), c);
            space(right.getAfter(), c);
        }
    }

    // Mirrors JavaPrinter.visitLeftPadded
    private void leftPadded(@Nullable JLeftPadded<? extends J> left, Counter c) {
        if (left != null) {
            space(left.getBefore(), c);
            visit(left.getElement(), c);
        }
    }

    // Mirrors JavaPrinter.visitContainer with ScalaPrinter.visitRightPadded
    private void container(@Nullable JContainer<? extends J> container, Counter c) {
        if (container != null) {
            space(container.getBefore(), c);
            List<? extends JRightPadded<? extends J>> elements = container.getPadding().getElements();
            for (int i = 0; i < elements.size(); i++) {
                JRightPadded<? extends J> element = elements.get(i);
                visit(element.getElement(), c);
                listElementSuffix(element, i == elements.size() - 1, c);
            }
        }
    }

    private void elements(@Nullable List<? extends J> elements, Counter c) {
        if (elements != null) {
            for (J element : elements) {
                visit(element, c);
            }
        }
    }

    private void statements(List<JRightPadded<Statement>> statements, Counter c) {
        for (JRightPadded<Statement> statement : statements) {
            visit(statement.getElement(), c);
            space(statement.getAfter(), c);
        }
    }

    @Override
    public J visitCompilationUnit(S.CompilationUnit cu, Counter c) {
        space(cu.getPrefix(), c);
        visit(cu.getPackageDeclaration(), c);
        elements(cu.getStatements(), c);
        space(cu.getEof(), c);
        return cu;
    }

    @Override
    public J visitPackage(J.Package pkg, Counter c) {
        space(pkg.getPrefix(), c);
        visit(pkg.getExpression(), c);
        return pkg;
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam, Counter c) {
        space(typeParam.getPrefix(), c);
        elements(typeParam.getAnnotations(), c);
        visit(typeParam.getName(), c);
        JContainer<TypeTree> bounds = typeParam.getPadding().getBounds();
        if (bounds != null) {
            for (TypeTree bound : bounds.getElements()) {
                if (bound instanceof J.TypeBound) {
                    space(bound.getPrefix(), c);
                    visit(((J.TypeBound) bound).getBoundedType(), c);
                } else {
                    space(bounds.getBefore(), c);
                    visit(bound, c);
                }
            }
        }
        return typeParam;
    }

    @Override
    public J visitFieldAccess(J.FieldAccess fieldAccess, Counter c) {
        if (isSyntheticPredefChain(fieldAccess)) {
            return fieldAccess;
        }
        if (fieldAccess.getMarkers().findFirst(TypeProjection.class).isPresent()) {
            space(fieldAccess.getPrefix(), c);
            visit(fieldAccess.getTarget(), c);
            visit(fieldAccess.getName(), c);
            return fieldAccess;
        }
        if (fieldAccess.getTarget() instanceof J.Empty) {
            space(fieldAccess.getPrefix(), c);
            visit(fieldAccess.getName(), c);
            return fieldAccess;
        }
        return super.visitFieldAccess(fieldAccess, c);
    }

    private static boolean isSyntheticPredefChain(J.FieldAccess fa) {
        String leaf = fa.getSimpleName();
        if (!"???".equals(leaf) && !"$qmark$qmark$qmark".equals(leaf)) {
            return false;
        }
        Expression target = fa.getTarget();
        while (target instanceof J.FieldAccess) {
            target = ((J.FieldAccess) target).getTarget();
        }
        return target instanceof J.Identifier && "_root_".equals(((J.Identifier) target).getSimpleName());
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop, Counter c) {
        if (!whileLoop.getMarkers().findFirst(IndentedSyntax.class).isPresent()) {
            return super.visitWhileLoop(whileLoop, c);
        }
        space(whileLoop.getPrefix(), c);
        J.ControlParentheses<Expression> cp = whileLoop.getCondition();
        space(cp.getPrefix(), c);
        rightPadded(cp.getPadding().getTree(), c);
        visit(whileLoop.getBody(), c);
        return whileLoop;
    }

    @Override
    public J visitIf(J.If iff, Counter c) {
        if (!iff.getMarkers().findFirst(IndentedSyntax.class).isPresent()) {
            return super.visitIf(iff, c);
        }
        space(iff.getPrefix(), c);
        J.ControlParentheses<Expression> cp = iff.getIfCondition();
        space(cp.getPrefix(), c);
        rightPadded(cp.getPadding().getTree(), c);
        visit(iff.getThenPart(), c);
        visit(iff.getElsePart(), c);
        return iff;
    }

    @Override
    public J visitTry(J.Try tryable, Counter c) {
        space(tryable.getPrefix(), c);
        visit(tryable.getBody(), c);
        boolean indentedCatch = tryable.getMarkers().findFirst(IndentedSyntax.class).isPresent();
        List<J.Try.Catch> catches = tryable.getCatches();
        for (int i = 0; i < catches.size(); i++) {
            J.Try.Catch aCatch = catches.get(i);
            if (i == 0) {
                space(aCatch.getPrefix(), c);
                if (!indentedCatch) {
                    space(aCatch.getParameter().getPrefix(), c);
                }
            }
            JRightPadded<J.VariableDeclarations> paramPadded = aCatch.getParameter().getPadding().getTree();
            J.VariableDeclarations varDecl = paramPadded.getElement();
            space(varDecl.getPrefix(), c);
            if (!varDecl.getVariables().isEmpty()) {
                visit(varDecl.getVariables().get(0).getName(), c);
            }
            if (varDecl.getTypeExpression() != null) {
                space(varDecl.getVarargs(), c);
                visit(varDecl.getTypeExpression(), c);
            }
            space(paramPadded.getAfter(), c);
            visit(aCatch.getBody(), c);
        }
        JLeftPadded<J.Block> finallyBlock = tryable.getPadding().getFinally();
        leftPadded(finallyBlock, c);
        return tryable;
    }

    @Override
    public J visitSTry(S.Try tryable, Counter c) {
        space(tryable.getPrefix(), c);
        visit(tryable.getBody(), c);
        JLeftPadded<J.Block> catches = tryable.getPadding().getCatches();
        if (catches != null) {
            space(catches.getBefore(), c);
            J.Block cases = catches.getElement();
            space(cases.getPrefix(), c);
            statements(cases.getPadding().getStatements(), c);
            space(cases.getEnd(), c);
        }
        leftPadded(tryable.getPadding().getFinalizer(), c);
        return tryable;
    }

    @Override
    public J visitSwitch(J.Switch switch_, Counter c) {
        space(switch_.getPrefix(), c);
        rightPadded(switch_.getSelector().getPadding().getTree(), c);
        J.Block cases = switch_.getCases();
        if (!switch_.getMarkers().findFirst(IndentedSyntax.class).isPresent()) {
            space(cases.getPrefix(), c);
        }
        statements(cases.getPadding().getStatements(), c);
        space(cases.getEnd(), c);
        return switch_;
    }

    @Override
    public J visitCase(J.Case case_, Counter c) {
        space(case_.getPrefix(), c);
        List<JRightPadded<J>> labels = case_.getPadding().getCaseLabels().getPadding().getElements();
        for (int i = 0; i < labels.size(); i++) {
            visit(labels.get(i).getElement(), c);
            if (i == labels.size() - 1) {
                space(labels.get(i).getAfter(), c);
            }
        }
        if (case_.getGuard() != null) {
            visit(case_.getGuard(), c);
            space(case_.getPadding().getStatements().getBefore(), c);
        }
        if (case_.getPadding().getBody() != null) {
            visit(case_.getPadding().getBody().getElement(), c);
        }
        return case_;
    }

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, Counter c) {
        space(method.getPrefix(), c);
        elements(method.getLeadingAnnotations(), c);
        for (J.Modifier m : method.getModifiers()) {
            if (m.getType() == J.Modifier.Type.LanguageExtension &&
                ("def".equals(m.getKeyword()) || "given".equals(m.getKeyword()))) {
                space(m.getPrefix(), c);
            } else {
                visit(m, c);
            }
        }
        visit(method.getName(), c);
        visit(method.getPadding().getTypeParameters(), c);

        JContainer<Statement> params = method.getPadding().getParameters();
        if (!params.getMarkers().findFirst(OmitBraces.class).isPresent()) {
            space(params.getBefore(), c);
        }
        parameters(params.getPadding().getElements(), true, c);

        J actualBody = null;
        boolean isCurried = method.getMarkers().findFirst(Curried.class).isPresent();
        if (isCurried && method.getBody() != null && !method.getBody().getStatements().isEmpty()) {
            J current = method.getBody().getStatements().get(0);
            while (current instanceof J.Lambda) {
                J.Lambda lambda = (J.Lambda) current;
                curriedParameters(lambda.getParameters(), c);
                if (lambda.getMarkers().findFirst(Curried.class).isPresent() && lambda.getBody() instanceof J.Lambda) {
                    current = lambda.getBody();
                } else {
                    actualBody = lambda.getBody();
                    break;
                }
            }
        }

        TypeTree returnType = method.getReturnTypeExpression();
        if (returnType != null) {
            returnType.getMarkers().findFirst(ReturnTypeColonPrefix.class).ifPresent(m -> space(m.getPrefix(), c));
            visit(returnType, c);
        }

        boolean procedureSyntax = method.getMarkers().findFirst(OmitBraces.class).isPresent();
        if (isCurried && actualBody != null) {
            J.Block block = actualBody instanceof J.Block ? (J.Block) actualBody : null;
            boolean isAbstract = block != null && block.getStatements().isEmpty() &&
                                 block.getMarkers().findFirst(OmitBraces.class).isPresent();
            if (!isAbstract) {
                if (!procedureSyntax) {
                    Space beforeEquals = method.getMarkers().findFirst(MethodBodyEqualsPrefix.class)
                            .map(MethodBodyEqualsPrefix::getPrefix)
                            .orElse(block != null ? block.getPrefix() : Space.SINGLE_SPACE);
                    space(beforeEquals, c);
                }
                if (block != null && block.getMarkers().findFirst(OmitBraces.class).isPresent() &&
                    block.getStatements().size() == 1) {
                    visit(block.getStatements().get(0), c);
                } else {
                    visit(actualBody, c);
                }
            }
        } else if (method.getBody() != null) {
            J.Block body = method.getBody();
            if (!procedureSyntax) {
                space(method.getMarkers().findFirst(MethodBodyEqualsPrefix.class)
                        .map(MethodBodyEqualsPrefix::getPrefix)
                        .orElse(body.getPrefix()), c);
            }
            if (body.getMarkers().findFirst(OmitBraces.class).isPresent() && body.getStatements().size() == 1) {
                space(body.getPrefix(), c);
                visit(body.getStatements().get(0), c);
            } else {
                visit(body, c);
            }
        }
        return method;
    }

    // Mirrors the parameter loops of ScalaPrinter's method, constructor and extension printing
    private void parameters(List<JRightPadded<Statement>> params, boolean honorOmitName, Counter c) {
        for (int i = 0; i < params.size(); i++) {
            JRightPadded<Statement> param = params.get(i);
            if (param.getElement() instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecl = (J.VariableDeclarations) param.getElement();
                space(varDecl.getPrefix(), c);
                elements(varDecl.getLeadingAnnotations(), c);
                elements(varDecl.getModifiers(), c);
                parameterNameTypeAndDefault(varDecl, honorOmitName, c);
            } else {
                visit(param.getElement(), c);
            }
            listElementSuffix(param, i == params.size() - 1, c);
        }
    }

    private void parameterNameTypeAndDefault(J.VariableDeclarations varDecl, boolean honorOmitName, Counter c) {
        List<J.VariableDeclarations.NamedVariable> variables = varDecl.getVariables();
        boolean omitName = honorOmitName && !variables.isEmpty() &&
                           variables.get(0).getMarkers().findFirst(OmitName.class).isPresent();
        if (!omitName && !variables.isEmpty()) {
            visit(variables.get(0).getName(), c);
        }
        if (varDecl.getTypeExpression() != null) {
            space(varDecl.getVarargs(), c);
            visit(varDecl.getTypeExpression(), c);
        }
        if (!variables.isEmpty()) {
            leftPadded(variables.get(0).getPadding().getInitializer(), c);
        }
    }

    // Mirrors ScalaPrinter.printLambdaParamsAsCurried
    private void curriedParameters(J.Lambda.Parameters lambdaParams, Counter c) {
        space(lambdaParams.getPrefix(), c);
        List<JRightPadded<J>> params = lambdaParams.getPadding().getParameters();
        for (int i = 0; i < params.size(); i++) {
            JRightPadded<J> param = params.get(i);
            if (param.getElement() instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecl = (J.VariableDeclarations) param.getElement();
                space(varDecl.getPrefix(), c);
                elements(varDecl.getLeadingAnnotations(), c);
                elements(varDecl.getModifiers(), c);
                parameterNameTypeAndDefault(varDecl, true, c);
            } else {
                visit(param.getElement(), c);
            }
            listElementSuffix(param, i == params.size() - 1, c);
        }
    }

    @Override
    public J visitImport(J.Import import_, Counter c) {
        space(import_.getPrefix(), c);
        J.FieldAccess qualid = import_.getQualid();
        String name = qualid.getName().getSimpleName();
        if ("*".equals(name) || "_".equals(name) || "given".equals(name)) {
            visit(qualid.getTarget(), c);
        } else {
            visit(qualid, c);
        }
        return import_;
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, Counter c) {
        boolean isObject = classDecl.getMarkers().findFirst(SObject.class).isPresent();
        JContainer<J.TypeParameter> typeParams = classDecl.getPadding().getTypeParameters();
        JContainer<Statement> primaryConstructor = classDecl.getPadding().getPrimaryConstructor();
        if (!isObject && classDecl.getKind() != J.ClassDeclaration.Kind.Type.Interface &&
            (classDecl.getImplements() == null || classDecl.getImplements().isEmpty()) &&
            primaryConstructor == null && (typeParams == null || typeParams.getElements().isEmpty())) {
            return super.visitClassDeclaration(classDecl, c);
        }

        space(classDecl.getPrefix(), c);
        elements(classDecl.getLeadingAnnotations(), c);
        for (J.Modifier m : classDecl.getModifiers()) {
            if (!(isObject && m.getType() == J.Modifier.Type.Final && m.getMarkers().findFirst(Implicit.class).isPresent())) {
                visit(m, c);
            }
        }
        elements(classDecl.getPadding().getKind().getAnnotations(), c);
        space(classDecl.getPadding().getKind().getPrefix(), c);
        visit(classDecl.getName(), c);
        if (typeParams != null && !typeParams.getElements().isEmpty()) {
            space(typeParams.getBefore(), c);
            List<JRightPadded<J.TypeParameter>> elements = typeParams.getPadding().getElements();
            for (int i = 0; i < elements.size(); i++) {
                visit(elements.get(i).getElement(), c);
                listElementSuffix(elements.get(i), i == elements.size() - 1, c);
            }
        }
        if (primaryConstructor != null && !primaryConstructor.getMarkers().findFirst(OmitParentheses.class).isPresent()) {
            space(primaryConstructor.getBefore(), c);
            parameters(primaryConstructor.getPadding().getElements(), true, c);
            primaryConstructor.getMarkers().findFirst(ExtraConstructorParamLists.class)
                    .ifPresent(m -> Newlines.addText(c, m.text()));
        }
        leftPadded(classDecl.getPadding().getExtends(), c);
        JContainer<TypeTree> implementations = classDecl.getPadding().getImplements();
        if (implementations != null) {
            space(implementations.getBefore(), c);
            List<JRightPadded<TypeTree>> elements = implementations.getPadding().getElements();
            for (int i = 0; i < elements.size(); i++) {
                visit(elements.get(i).getElement(), c);
                if (i < elements.size() - 1) {
                    space(elements.get(i).getAfter(), c);
                }
            }
        }
        container(classDecl.getPadding().getPermits(), c);
        visit(classDecl.getBody(), c);
        return classDecl;
    }

    @Override
    public J visitBlock(J.Block block, Counter c) {
        space(block.getPrefix(), c);
        boolean braced = !block.getMarkers().findFirst(OmitBraces.class).isPresent() &&
                         !block.getMarkers().findFirst(IndentedSyntax.class).isPresent();
        if (braced && block.isStatic()) {
            space(block.getPadding().getStatic().getAfter(), c);
        }
        statements(block.getPadding().getStatements(), c);
        space(block.getEnd(), c);
        return block;
    }

    @Override
    public J visitReturn(J.Return return_, Counter c) {
        if (return_.getMarkers().findFirst(ImplicitReturn.class).isPresent()) {
            space(return_.getPrefix(), c);
            visit(return_.getExpression(), c);
            return return_;
        }
        return super.visitReturn(return_, c);
    }

    @Override
    public J visitForEachLoop(J.ForEachLoop forEachLoop, Counter c) {
        if (!forEachLoop.getMarkers().findFirst(ScalaForLoop.class).isPresent()) {
            return super.visitForEachLoop(forEachLoop, c);
        }
        space(forEachLoop.getPrefix(), c);
        J.ForEachLoop.Control ctrl = forEachLoop.getControl();
        space(ctrl.getPrefix(), c);
        JRightPadded<Statement> variable = ctrl.getPadding().getVariable();
        if (variable.getElement() instanceof J.VariableDeclarations) {
            J.VariableDeclarations varDecl = (J.VariableDeclarations) variable.getElement();
            space(varDecl.getPrefix(), c);
            if (!varDecl.getVariables().isEmpty()) {
                visit(varDecl.getVariables().get(0).getName(), c);
            }
        } else {
            visit(variable.getElement(), c);
        }
        space(variable.getAfter(), c);
        rightPadded(ctrl.getPadding().getIterable(), c);
        rightPadded(forEachLoop.getPadding().getBody(), c);
        return forEachLoop;
    }

    @Override
    public J visitTypeAscription(S.TypeAscription typeAscription, Counter c) {
        typeAscription.getMarkers().findFirst(TypeAscriptionColonPrefix.class).ifPresent(m -> space(m.getPrefix(), c));
        return super.visitTypeAscription(typeAscription, c);
    }

    @Override
    public J visitFunctionCall(S.FunctionCall fc, Counter c) {
        space(fc.getPrefix(), c);
        rightPadded(fc.getPadding().getFunction(), c);
        if (fc.getMarkers().findFirst(BlockArgument.class).isPresent() ||
            fc.getMarkers().findFirst(IndentedSyntax.class).isPresent()) {
            elements(fc.getArguments(), c);
        } else {
            container(fc.getPadding().getArguments(), c);
        }
        return fc;
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast, Counter c) {
        space(typeCast.getPrefix(), c);
        visit(typeCast.getExpression(), c);
        typeCast.getMarkers().findFirst(AsInstanceOfPrefix.class).ifPresent(m -> space(m.getPrefix(), c));
        if (typeCast.getClazz() instanceof J.ControlParentheses) {
            J.ControlParentheses<?> parens = typeCast.getClazz();
            space(parens.getPrefix(), c);
            rightPadded(parens.getPadding().getTree(), c);
        }
        return typeCast;
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, Counter c) {
        space(multiVariable.getPrefix(), c);
        elements(multiVariable.getLeadingAnnotations(), c);
        Space valVarPrefix = null;
        for (J.Modifier m : multiVariable.getModifiers()) {
            if (m.getType() == J.Modifier.Type.Final && !"final".equals(m.getKeyword())) {
                valVarPrefix = m.getPrefix();
            } else {
                visit(m, c);
            }
        }
        if (!multiVariable.getMarkers().findFirst(LambdaParameter.class).isPresent()) {
            if (valVarPrefix != null) {
                space(valVarPrefix, c);
            } else {
                multiVariable.getMarkers().findFirst(ValVarKeyword.class).ifPresent(m -> Newlines.addText(c, m.beforeKeyword()));
            }
        }
        List<JRightPadded<J.VariableDeclarations.NamedVariable>> variables = multiVariable.getPadding().getVariables();
        for (int i = 0; i < variables.size(); i++) {
            JRightPadded<J.VariableDeclarations.NamedVariable> variable = variables.get(i);
            variable(variable.getElement(), multiVariable, c);
            listElementSuffix(variable, i == variables.size() - 1, c);
        }
        return multiVariable;
    }

    @Override
    public J visitVariable(J.VariableDeclarations.NamedVariable variable, Counter c) {
        J.VariableDeclarations parent = getCursor().firstEnclosing(J.VariableDeclarations.class);
        if (parent == null) {
            return super.visitVariable(variable, c);
        }
        variable(variable, parent, c);
        return variable;
    }

    // Mirrors ScalaPrinter.visitVariable, which prints the declaration's type after each variable
    private void variable(J.VariableDeclarations.NamedVariable variable, J.VariableDeclarations parent, Counter c) {
        space(variable.getPrefix(), c);
        if (!variable.getMarkers().findFirst(OmitName.class).isPresent()) {
            visit(variable.getName(), c);
        }
        if (parent.getTypeExpression() != null) {
            space(parent.getVarargs(), c);
            visit(parent.getTypeExpression(), c);
        }
        leftPadded(variable.getPadding().getInitializer(), c);
    }

    @Override
    public J visitNewClass(J.NewClass newClass, Counter c) {
        space(newClass.getPrefix(), c);
        rightPadded(newClass.getPadding().getEnclosing(), c);
        if (newClass.getClazz() instanceof J.IntersectionType && newClass.getPadding().getArguments() != null) {
            J.IntersectionType intersection = (J.IntersectionType) newClass.getClazz();
            space(intersection.getPrefix(), c);
            JContainer<TypeTree> bounds = intersection.getPadding().getBounds();
            space(bounds.getBefore(), c);
            List<JRightPadded<TypeTree>> elements = bounds.getPadding().getElements();
            if (!elements.isEmpty()) {
                visit(elements.get(0).getElement(), c);
                container(newClass.getPadding().getArguments(), c);
                extraConstructorParamLists(newClass, c);
                for (int i = 1; i < elements.size(); i++) {
                    space(elements.get(i - 1).getAfter(), c);
                    visit(elements.get(i).getElement(), c);
                }
            }
        } else {
            visit(newClass.getClazz(), c);
            container(newClass.getPadding().getArguments(), c);
            extraConstructorParamLists(newClass, c);
        }
        visit(newClass.getBody(), c);
        return newClass;
    }

    private static void extraConstructorParamLists(J.NewClass newClass, Counter c) {
        newClass.getMarkers().findFirst(ExtraConstructorParamLists.class).ifPresent(m -> Newlines.addText(c, m.text()));
    }

    @Override
    public J visitParameterizedType(J.ParameterizedType type, Counter c) {
        JContainer<Expression> typeParameters = type.getPadding().getTypeParameters();
        if (type.getMarkers().findFirst(InfixTypeNotation.class).isPresent() && typeParameters != null &&
            typeParameters.getPadding().getElements().size() == 2) {
            space(type.getPrefix(), c);
            List<JRightPadded<Expression>> operands = typeParameters.getPadding().getElements();
            rightPadded(operands.get(0), c);
            visit(type.getClazz(), c);
            visit(operands.get(1).getElement(), c);
            return type;
        }
        space(type.getPrefix(), c);
        visit(type.getClazz(), c);
        container(typeParameters, c);
        return type;
    }

    @Override
    public J visitInstanceOf(J.InstanceOf instanceOf, Counter c) {
        space(instanceOf.getPrefix(), c);
        rightPadded(instanceOf.getPadding().getExpression(), c);
        visit(instanceOf.getClazz(), c);
        return instanceOf;
    }

    @Override
    public J visitNewArray(J.NewArray newArray, Counter c) {
        space(newArray.getPrefix(), c);
        visit(newArray.getTypeExpression(), c);
        container(newArray.getPadding().getInitializer(), c);
        return newArray;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, Counter c) {
        Markers markers = method.getMarkers();
        boolean functionApplication = markers.findFirst(FunctionApplication.class).isPresent();
        JContainer<Expression> typeParameters = method.getPadding().getTypeParameters();
        boolean hasTypeParameters = typeParameters != null && !typeParameters.getElements().isEmpty();
        JContainer<Expression> args = method.getPadding().getArguments();
        if (markers.findFirst(IndentedSyntax.class).isPresent() || markers.findFirst(BlockArgument.class).isPresent()) {
            space(method.getPrefix(), c);
            rightPadded(method.getPadding().getSelect(), c);
            if (!functionApplication) {
                visit(method.getName(), c);
            }
            if (hasTypeParameters) {
                container(typeParameters, c);
            }
            if (markers.findFirst(IndentedSyntax.class).isPresent() && args != null) {
                space(args.getBefore(), c);
            }
            elements(method.getArguments(), c);
            return method;
        }
        if (functionApplication) {
            space(method.getPrefix(), c);
            rightPadded(method.getPadding().getSelect(), c);
            container(args, c);
            return method;
        }
        if (markers.findFirst(InfixNotation.class).isPresent()) {
            space(method.getPrefix(), c);
            if (markers.findFirst(RightAssociative.class).isPresent()) {
                elements(method.getArguments(), c);
                visit(method.getName(), c);
                rightPadded(method.getPadding().getSelect(), c);
            } else {
                rightPadded(method.getPadding().getSelect(), c);
                visit(method.getName(), c);
                elements(method.getArguments(), c);
            }
            return method;
        }
        boolean omitParentheses = args != null && args.getMarkers().findFirst(OmitParentheses.class).isPresent();
        if (hasTypeParameters || omitParentheses) {
            space(method.getPrefix(), c);
            rightPadded(method.getPadding().getSelect(), c);
            visit(method.getName(), c);
            if (hasTypeParameters) {
                container(typeParameters, c);
            }
            if (!omitParentheses) {
                container(args, c);
            }
            return method;
        }
        space(method.getPrefix(), c);
        rightPadded(method.getPadding().getSelect(), c);
        container(typeParameters, c);
        visit(method.getName(), c);
        container(args, c);
        return method;
    }

    @Override
    public J visitMemberReference(J.MemberReference memberRef, Counter c) {
        space(memberRef.getPrefix(), c);
        rightPadded(memberRef.getPadding().getContaining(), c);
        visit(memberRef.getPadding().getReference().getElement(), c);
        return memberRef;
    }

    @Override
    public J visitLambda(J.Lambda lambda, Counter c) {
        space(lambda.getPrefix(), c);
        if (lambda.getMarkers().findFirst(UnderscorePlaceholderLambda.class).isPresent()) {
            visit(lambda.getBody(), c);
            return lambda;
        }
        if (lambda.getMarkers().findFirst(PartialFunctionLiteral.class).isPresent() && lambda.getBody() instanceof J.Block) {
            J.Block cases = (J.Block) lambda.getBody();
            statements(cases.getPadding().getStatements(), c);
            space(cases.getEnd(), c);
            return lambda;
        }
        J.Lambda.Parameters params = lambda.getParameters();
        space(params.getPrefix(), c);
        List<JRightPadded<J>> elements = params.getPadding().getParameters();
        for (int i = 0; i < elements.size(); i++) {
            visit(elements.get(i).getElement(), c);
            listElementSuffix(elements.get(i), i == elements.size() - 1, c);
        }
        space(lambda.getArrow(), c);
        visit(lambda.getBody(), c);
        return lambda;
    }

    @Override
    public J visitTypeAlias(S.TypeAlias typeAlias, Counter c) {
        Newlines.addText(c, typeAlias.getText());
        return super.visitTypeAlias(typeAlias, c);
    }

    @Override
    public J visitExport(S.Export export, Counter c) {
        Expression clause = export.getExportClause();
        if (export.getPadding().getSelectors() == null && clause instanceof J.FieldAccess) {
            J.FieldAccess fa = (J.FieldAccess) clause;
            String name = fa.getName().getSimpleName();
            if ("*".equals(name) || "_".equals(name) || "given".equals(name)) {
                space(export.getPrefix(), c);
                visit(fa.getTarget(), c);
                return export;
            }
        }
        return super.visitExport(export, c);
    }

    @Override
    public J visitImportSelector(S.ImportSelector selector, Counter c) {
        space(selector.getPrefix(), c);
        if (selector.isGiven()) {
            visit(selector.getGivenType(), c);
        } else if (!selector.isWildcard() && selector.getName() != null) {
            visit(selector.getName(), c);
            leftPadded(selector.getPadding().getAlias(), c);
        }
        return selector;
    }

    @Override
    public J visitPatternDefinition(S.PatternDefinition patDef, Counter c) {
        Newlines.addText(c, patDef.getText());
        return super.visitPatternDefinition(patDef, c);
    }

    @Override
    public J visitAnonymousGiven(S.AnonymousGiven given, Counter c) {
        given.getMarkers().findFirst(ValVarKeyword.class).ifPresent(m -> Newlines.addText(c, m.beforeKeyword()));
        return super.visitAnonymousGiven(given, c);
    }

    @Override
    public J visitXmlLiteral(S.XmlLiteral xmlLiteral, Counter c) {
        Newlines.addText(c, xmlLiteral.getSource());
        return super.visitXmlLiteral(xmlLiteral, c);
    }

    @Override
    public J visitInterpolatedString(S.InterpolatedString interpolatedString, Counter c) {
        Newlines.addText(c, interpolatedString.getDelimiter());
        return super.visitInterpolatedString(interpolatedString, c);
    }

    @Override
    public J visitExtensionMethods(S.ExtensionMethods ext, Counter c) {
        space(ext.getPrefix(), c);
        visit(ext.getTypeParameters(), c);
        JContainer<Statement> params = ext.getPadding().getParameters();
        // ScalaPrinter prints only the whitespace before the parameters, never comments
        Newlines.addText(c, params.getBefore().getWhitespace());
        parameters(params.getPadding().getElements(), false, c);
        visit(ext.getBody(), c);
        return ext;
    }
}
