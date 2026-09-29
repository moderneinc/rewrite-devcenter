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
import org.openrewrite.java.marker.OmitBraces;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.AnonymousFunction;
import org.openrewrite.kotlin.marker.Extension;
import org.openrewrite.kotlin.marker.Implicit;
import org.openrewrite.kotlin.marker.IndexedAccess;
import org.openrewrite.kotlin.marker.PrimaryConstructor;
import org.openrewrite.kotlin.marker.TrailingLambdaArgument;
import org.openrewrite.kotlin.marker.TypeReferencePrefix;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.util.List;

/**
 * Counts lines in a Kotlin LST in-process from its source text, without reconstructing the source string.
 * Where {@code KotlinPrinter} prints a different set of spaces than the visitor visits (implicit nodes,
 * primary constructors, properties, receivers, ...), the traversal mirrors the printer.
 * Text that markers would print isn't source and isn't counted.
 */
final class KotlinLineCounter extends KotlinVisitor<Counter> {

    private KotlinLineCounter() {
    }

    static long count(K.CompilationUnit cu) {
        Counter c = new Counter();
        new KotlinLineCounter().visit(cu, c);
        return Newlines.lineCount(c, cu.getEof());
    }

    @Override
    public @Nullable J preVisit(J tree, Counter c) {
        if (!c.sawText && printsSyntax(tree)) {
            c.sawText = true;
        }
        return tree;
    }

    // Wrappers, brace-less blocks and empty or implicit nodes print no text of their own
    private static boolean printsSyntax(J tree) {
        if (tree instanceof K.CompilationUnit || tree instanceof J.Empty || tree instanceof K.ExpressionStatement ||
            tree instanceof K.StatementExpression || tree instanceof J.Lambda.Parameters || isImplicit(tree)) {
            return false;
        }
        return !(tree instanceof J.Block) ||
               !tree.getMarkers().findFirst(OmitBraces.class).isPresent() &&
               !tree.getMarkers().findFirst(org.openrewrite.kotlin.marker.OmitBraces.class).isPresent();
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, Counter c) {
        Newlines.addSpace(c, space);
        return space;
    }

    @Override
    public J visitLiteral(J.Literal literal, Counter c) {
        beforeSyntax(literal, c);
        Newlines.addText(c, literal.getValueSource());
        return literal;
    }

    @Override
    public J visitUnknownSource(J.Unknown.Source source, Counter c) {
        beforeSyntax(source, c);
        Newlines.addText(c, source.getText());
        return source;
    }

    @Override
    public J visitErroneous(J.Erroneous erroneous, Counter c) {
        beforeSyntax(erroneous, c);
        Newlines.addText(c, erroneous.getText());
        return erroneous;
    }

    @Override
    public <M extends Marker> M visitMarker(Marker marker, Counter c) {
        if (marker instanceof TrailingComma) {
            Newlines.addSpace(c, ((TrailingComma) marker).getSuffix());
        }
        // A TypeReferencePrefix is counted only where KotlinPrinter prints it
        //noinspection unchecked
        return (M) marker;
    }

    @Override
    public J visitCompilationUnit(K.CompilationUnit cu, Counter c) {
        Newlines.addText(c, cu.getShebang());
        beforeSyntax(cu.getPrefix(), cu.getMarkers(), c);
        visit(cu.getAnnotations(), c);
        rightPadded(cu.getPadding().getPackageDeclaration(), c);
        for (JRightPadded<J.Import> anImport : cu.getPadding().getImports()) {
            rightPadded(anImport, c);
        }
        for (JRightPadded<Statement> statement : cu.getPadding().getStatements()) {
            rightPadded(statement, c);
        }
        visitSpace(cu.getEof(), Space.Location.COMPILATION_UNIT_EOF, c);
        return cu;
    }

    @Override
    public J visitBinary(K.Binary binary, Counter c) {
        super.visitBinary(binary, c);
        visitSpace(binary.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
        return binary;
    }

    @Override
    public J visitBlock(J.Block block, Counter c) {
        beforeSyntax(block, c);
        if (block.isStatic()) {
            JRightPadded<Boolean> isStatic = block.getPadding().getStatic();
            visitSpace(isStatic.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
            visitMarkers(isStatic.getMarkers(), c);
        }
        for (JRightPadded<Statement> statement : block.getPadding().getStatements()) {
            if (!isImplicit(statement.getElement())) {
                rightPadded(statement, c);
            }
        }
        visitSpace(block.getEnd(), Space.Location.BLOCK_END, c);
        return block;
    }

    @Override
    public J visitClassDeclaration(K.ClassDeclaration classDeclaration, Counter c) {
        classDeclaration(classDeclaration.getClassDeclaration(), classDeclaration.getTypeConstraints(), c);
        return classDeclaration;
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, Counter c) {
        classDeclaration(classDecl, null, c);
        return classDecl;
    }

    private void classDeclaration(J.ClassDeclaration classDecl, K.@Nullable TypeConstraints typeConstraints, Counter c) {
        beforeSyntax(classDecl, c);
        visit(classDecl.getLeadingAnnotations(), c);
        visit(classDecl.getModifiers(), c);
        J.ClassDeclaration.Kind kind = classDecl.getPadding().getKind();
        visit(kind.getAnnotations(), c);
        visitSpace(kind.getPrefix(), Space.Location.CLASS_KIND, c);
        visit(classDecl.getName(), c);
        container(classDecl.getPadding().getTypeParameters(), c);
        // The primary constructor sits in the body but is printed in the header
        if (classDecl.getMarkers().findFirst(PrimaryConstructor.class).isPresent()) {
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration &&
                    statement.getMarkers().findFirst(PrimaryConstructor.class).isPresent() &&
                    !isImplicit(statement)) {
                    J.MethodDeclaration method = (J.MethodDeclaration) statement;
                    beforeSyntax(method, c);
                    visit(method.getLeadingAnnotations(), c);
                    visit(method.getModifiers(), c);
                    parameters(method.getPadding().getParameters(), 0, c);
                    break;
                }
            }
        }
        container(classDecl.getPadding().getImplements(), c);
        if (typeConstraints != null) {
            container(typeConstraints.getPadding().getConstraints(), c);
        }
        if (!classDecl.getBody().getMarkers().findFirst(OmitBraces.class).isPresent()) {
            visit(classDecl.getBody(), c);
        }
    }

    @Override
    public J visitConstructor(K.Constructor constructor, Counter c) {
        J.MethodDeclaration method = constructor.getMethodDeclaration();
        beforeSyntax(method, c);
        visit(method.getLeadingAnnotations(), c);
        visit(method.getModifiers(), c);
        parameters(method.getPadding().getParameters(), 0, c);
        visitSpace(constructor.getPadding().getInvocation().getBefore(), Space.Location.LANGUAGE_EXTENSION, c);
        visit(constructor.getInvocation(), c);
        visit(method.getBody(), c);
        return constructor;
    }

    @Override
    public J visitConstructorInvocation(K.ConstructorInvocation constructorInvocation, Counter c) {
        beforeSyntax(constructorInvocation.getPrefix(), constructorInvocation.getMarkers(), c);
        visit(constructorInvocation.getTypeTree(), c);
        arguments(constructorInvocation.getPadding().getArguments(), c);
        return constructorInvocation;
    }

    @Override
    @SuppressWarnings("deprecation")
    public J visitDestructuringDeclaration(K.DestructuringDeclaration destructuringDeclaration, Counter c) {
        beforeSyntax(destructuringDeclaration.getPrefix(), destructuringDeclaration.getMarkers(), c);
        J.VariableDeclarations initializer = destructuringDeclaration.getInitializer();
        visit(initializer.getLeadingAnnotations(), c);
        visit(initializer.getModifiers(), c);
        JContainer<Statement> assignments = destructuringDeclaration.getPadding().getDestructAssignments();
        visitSpace(assignments.getBefore(), Space.Location.LANGUAGE_EXTENSION, c);
        for (JRightPadded<Statement> assignment : assignments.getPadding().getElements()) {
            rightPadded(assignment, c);
        }
        if (!initializer.getVariables().isEmpty()) {
            JLeftPadded<Expression> value = initializer.getVariables().get(0).getPadding().getInitializer();
            if (value != null) {
                visitSpace(value.getBefore(), Space.Location.LANGUAGE_EXTENSION, c);
                visit(value.getElement(), c);
            }
        }
        return destructuringDeclaration;
    }

    @Override
    public J visitFunctionType(K.FunctionType functionType, Counter c) {
        beforeSyntax(functionType.getPrefix(), functionType.getMarkers(), c);
        visit(functionType.getLeadingAnnotations(), c);
        visit(functionType.getContextParameters(), c);
        visit(functionType.getModifiers(), c);
        rightPadded(functionType.getReceiver(), c);
        container(functionType.getPadding().getParameters(), c);
        if (functionType.getArrow() != null) {
            visitSpace(functionType.getArrow(), Space.Location.LANGUAGE_EXTENSION, c);
        }
        rightPadded(functionType.getReturnType(), c);
        return functionType;
    }

    @Override
    public J visitFunctionTypeParameter(K.FunctionType.Parameter parameter, Counter c) {
        if (parameter.getName() != null) {
            visit(parameter.getName(), c);
            typeReferencePrefix(parameter.getMarkers(), c);
        }
        visit(parameter.getParameterType(), c);
        return parameter;
    }

    @Override
    public J visitIdentifier(J.Identifier ident, Counter c) {
        if (!isImplicit(ident)) {
            visit(ident.getAnnotations(), c);
            beforeSyntax(ident, c);
        }
        return ident;
    }

    @Override
    public J visitImport(J.Import anImport, Counter c) {
        beforeSyntax(anImport, c);
        J.FieldAccess qualid = anImport.getQualid();
        visit(qualid.getTarget() instanceof J.Empty ? qualid.getName() : qualid, c);
        JLeftPadded<J.Identifier> alias = anImport.getPadding().getAlias();
        if (alias != null) {
            visitSpace(alias.getBefore(), Space.Location.IMPORT_ALIAS_PREFIX, c);
            visit(alias.getElement(), c);
        }
        return anImport;
    }

    @Override
    public J visitLambda(J.Lambda lambda, Counter c) {
        beforeSyntax(lambda, c);
        J.Lambda.Parameters parameters = lambda.getParameters();
        if (!lambda.getMarkers().findFirst(AnonymousFunction.class).isPresent()) {
            visitSpace(parameters.getPrefix(), Space.Location.LAMBDA_PARAMETER, c);
        }
        lambdaParameters(parameters, c);
        if (!lambda.getMarkers().findFirst(AnonymousFunction.class).isPresent() && !parameters.getParameters().isEmpty()) {
            visitSpace(lambda.getArrow(), Space.Location.LAMBDA_ARROW_PREFIX, c);
        }
        visit(lambda.getBody(), c);
        return lambda;
    }

    @Override
    public J visitLambdaParameters(J.Lambda.Parameters parameters, Counter c) {
        lambdaParameters(parameters, c);
        return parameters;
    }

    private void lambdaParameters(J.Lambda.Parameters parameters, Counter c) {
        visitMarkers(parameters.getMarkers(), c);
        if (parameters.isParenthesized()) {
            visitSpace(parameters.getPrefix(), Space.Location.LAMBDA_PARAMETERS_PREFIX, c);
            for (JRightPadded<J> parameter : parameters.getPadding().getParameters()) {
                rightPadded(parameter, c);
            }
            return;
        }
        for (JRightPadded<J> parameter : parameters.getPadding().getParameters()) {
            if (parameter.getElement() instanceof J.Lambda.Parameters) {
                lambdaParameters((J.Lambda.Parameters) parameter.getElement(), c);
                visitSpace(parameter.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
            } else {
                rightPadded(parameter, c);
            }
        }
    }

    @Override
    public J visitMethodDeclaration(K.MethodDeclaration methodDeclaration, Counter c) {
        methodDeclaration(methodDeclaration.getMethodDeclaration(), methodDeclaration.getTypeConstraints(),
                methodDeclaration.getContextParameters(), c);
        return methodDeclaration;
    }

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, Counter c) {
        methodDeclaration(method, null, null, c);
        return method;
    }

    private void methodDeclaration(J.MethodDeclaration method, K.@Nullable TypeConstraints typeConstraints,
                                   K.@Nullable ContextParameters contextParameters, Counter c) {
        if (isImplicit(method) || method.getMarkers().findFirst(PrimaryConstructor.class).isPresent()) {
            return;
        }
        beforeSyntax(method, c);
        visit(method.getLeadingAnnotations(), c);
        visit(contextParameters, c);
        visit(method.getModifiers(), c);
        J.TypeParameters typeParameters = method.getAnnotations().getTypeParameters();
        if (typeParameters != null) {
            visit(typeParameters.getAnnotations(), c);
            beforeSyntax(typeParameters.getPrefix(), typeParameters.getMarkers(), c);
            for (JRightPadded<J.TypeParameter> typeParameter : typeParameters.getPadding().getTypeParameters()) {
                rightPadded(typeParameter, c);
            }
        }
        boolean hasReceiver = method.getMarkers().findFirst(Extension.class).isPresent();
        if (hasReceiver) {
            J.VariableDeclarations receiver = (J.VariableDeclarations) method.getParameters().get(0);
            rightPadded(receiver.getPadding().getVariables().get(0), c);
        }
        if (!isImplicit(method.getName())) {
            visit(method.getAnnotations().getName().getAnnotations(), c);
            visit(method.getName(), c);
        }
        parameters(method.getPadding().getParameters(), hasReceiver ? 1 : 0, c);
        if (method.getReturnTypeExpression() != null) {
            typeReferencePrefix(method.getMarkers(), c);
            visit(method.getReturnTypeExpression(), c);
        }
        if (typeConstraints != null) {
            container(typeConstraints.getPadding().getConstraints(), c);
        }
        visit(method.getBody(), c);
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, Counter c) {
        beforeSyntax(method, c);
        rightPadded(method.getPadding().getSelect(), c);
        if (!method.getMarkers().findFirst(IndexedAccess.class).isPresent()) {
            visit(method.getName(), c);
            container(method.getPadding().getTypeParameters(), c);
        }
        arguments(method.getPadding().getArguments(), c);
        return method;
    }

    @Override
    public J visitNewClass(J.NewClass newClass, Counter c) {
        beforeSyntax(newClass, c);
        typeReferencePrefix(newClass.getMarkers(), c);
        rightPadded(newClass.getPadding().getEnclosing(), c);
        visitSpace(newClass.getNew(), Space.Location.NEW_PREFIX, c);
        visit(newClass.getClazz(), c);
        arguments(newClass.getPadding().getArguments(), c);
        visit(newClass.getBody(), c);
        return newClass;
    }

    @Override
    public J visitProperty(K.Property property, Counter c) {
        beforeSyntax(property.getPrefix(), property.getMarkers(), c);
        J.VariableDeclarations vd = property.getVariableDeclarations();
        visit(vd.getLeadingAnnotations(), c);
        visit(property.getContextParameters(), c);
        visit(vd.getModifiers(), c);
        container(property.getPadding().getTypeParameters(), c);
        if (vd.getMarkers().findFirst(Extension.class).isPresent()) {
            rightPadded(property.getPadding().getReceiver(), c);
        }
        List<JRightPadded<J.VariableDeclarations.NamedVariable>> variables = vd.getPadding().getVariables();
        if (!variables.isEmpty()) {
            JRightPadded<J.VariableDeclarations.NamedVariable> variable = variables.get(0);
            J.VariableDeclarations.NamedVariable nv = variable.getElement();
            beforeSyntax(nv, c);
            visit(nv.getName(), c);
            visitSpace(variable.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
            if (vd.getTypeExpression() != null) {
                typeReferencePrefix(vd.getMarkers(), c);
                visit(vd.getTypeExpression(), c);
            }
            JLeftPadded<Expression> initializer = nv.getPadding().getInitializer();
            if (initializer != null) {
                visitSpace(initializer.getBefore(), Space.Location.VARIABLE_INITIALIZER, c);
                visit(initializer.getElement(), c);
            }
        }
        if (property.getTypeConstraints() != null) {
            container(property.getTypeConstraints().getPadding().getConstraints(), c);
        }
        visitSpace(property.getPadding().getVariableDeclarations().getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
        J.VariableDeclarations backingField = property.getBackingField();
        if (backingField != null) {
            beforeSyntax(backingField, c);
            visit(backingField.getLeadingAnnotations(), c);
            visit(backingField.getModifiers(), c);
            JRightPadded<J.VariableDeclarations.NamedVariable> variable = backingField.getPadding().getVariables().get(0);
            J.VariableDeclarations.NamedVariable nv = variable.getElement();
            beforeSyntax(nv, c);
            visit(nv.getName(), c);
            visitSpace(variable.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
            visit(backingField.getTypeExpression(), c);
            JLeftPadded<Expression> initializer = nv.getPadding().getInitializer();
            if (initializer != null) {
                visitSpace(initializer.getBefore(), Space.Location.VARIABLE_INITIALIZER, c);
                visit(initializer.getElement(), c);
            }
        }
        visitContainer(property.getAccessors(), c);
        return property;
    }

    @Override
    public J visitReturn(K.Return kReturn, Counter c) {
        J.Return jReturn = kReturn.getExpression();
        if (kReturn.getLabel() != null) {
            beforeSyntax(jReturn, c);
            visit(kReturn.getLabel(), c);
            visit(jReturn.getExpression(), c);
        } else {
            visit(jReturn, c);
        }
        return kReturn;
    }

    @Override
    public J visitStringTemplateExpression(K.StringTemplate.Expression expression, Counter c) {
        beforeSyntax(expression.getPrefix(), expression.getMarkers(), c);
        visit(expression.getTree(), c);
        if (expression.isEnclosedInBraces()) {
            visitSpace(expression.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
        }
        return expression;
    }

    @Override
    public J visitTypeAlias(K.TypeAlias typeAlias, Counter c) {
        beforeSyntax(typeAlias.getPrefix(), typeAlias.getMarkers(), c);
        visit(typeAlias.getLeadingAnnotations(), c);
        visit(typeAlias.getModifiers(), c);
        visit(typeAlias.getName(), c);
        container(typeAlias.getPadding().getTypeParameters(), c);
        JLeftPadded<Expression> initializer = typeAlias.getPadding().getInitializer();
        if (initializer != null) {
            beforeSyntax(initializer.getBefore(), initializer.getMarkers(), c);
            visit(initializer.getElement(), c);
        }
        return typeAlias;
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast, Counter c) {
        beforeSyntax(typeCast, c);
        visit(typeCast.getExpression(), c);
        J.ControlParentheses<TypeTree> clazz = typeCast.getClazz();
        beforeSyntax(clazz, c);
        visit(clazz.getTree(), c);
        return typeCast;
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam, Counter c) {
        beforeSyntax(typeParam, c);
        visit(typeParam.getAnnotations(), c);
        //noinspection ConstantValue
        if (typeParam.getModifiers() != null) {
            visit(typeParam.getModifiers(), c);
        }
        visit(typeParam.getName(), c);
        if (typeParam.getBounds() != null && !typeParam.getBounds().isEmpty()) {
            typeReferencePrefix(typeParam.getMarkers(), c);
        }
        container(typeParam.getPadding().getBounds(), c);
        return typeParam;
    }

    @Override
    public J visitUnary(J.Unary unary, Counter c) {
        if (unary.getOperator() == J.Unary.Type.Not && unary.getExpression() instanceof K.Binary &&
            ((K.Binary) unary.getExpression()).getOperator() == K.Binary.Type.NotContains) {
            // The "!" is printed as part of "!in"
            beforeSyntax(unary, c);
            visit(unary.getExpression(), c);
            return unary;
        }
        return super.visitUnary(unary, c);
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, Counter c) {
        beforeSyntax(multiVariable, c);
        visit(multiVariable.getLeadingAnnotations(), c);
        visit(multiVariable.getModifiers(), c);
        for (JRightPadded<J.VariableDeclarations.NamedVariable> variable : multiVariable.getPadding().getVariables()) {
            J.VariableDeclarations.NamedVariable nv = variable.getElement();
            beforeSyntax(nv, c);
            visit(nv.getDeclarator(), c);
            visitSpace(variable.getAfter(), Space.Location.VARIABLE_INITIALIZER, c);
            visit(multiVariable.getTypeExpression(), c);
            JLeftPadded<Expression> initializer = nv.getPadding().getInitializer();
            if (initializer != null) {
                visitSpace(initializer.getBefore(), Space.Location.VARIABLE_INITIALIZER, c);
                visit(initializer.getElement(), c);
            }
        }
        return multiVariable;
    }

    @Override
    public J visitVariable(J.VariableDeclarations.NamedVariable variable, Counter c) {
        beforeSyntax(variable, c);
        if (!variable.getMarkers().findFirst(Extension.class).isPresent()) {
            visit(variable.getName(), c);
        }
        JLeftPadded<Expression> initializer = variable.getPadding().getInitializer();
        if (initializer != null) {
            beforeSyntax(initializer.getBefore(), initializer.getMarkers(), c);
            visit(initializer.getElement(), c);
        }
        return variable;
    }

    @Override
    public J visitWhenBranch(K.WhenBranch whenBranch, Counter c) {
        beforeSyntax(whenBranch.getPrefix(), whenBranch.getMarkers(), c);
        JContainer<Expression> expressions = whenBranch.getPadding().getExpressions();
        visitSpace(expressions.getBefore(), Space.Location.LANGUAGE_EXTENSION, c);
        for (JRightPadded<Expression> expression : expressions.getPadding().getElements()) {
            rightPadded(expression, c);
        }
        JRightPadded<Expression> guard = whenBranch.getPadding().getGuard();
        if (guard != null) {
            visit(guard.getElement(), c);
            visitSpace(guard.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
        }
        visit(whenBranch.getBody(), c);
        return whenBranch;
    }

    @Override
    public J visitWildcard(J.Wildcard wildcard, Counter c) {
        beforeSyntax(wildcard, c);
        visit(wildcard.getBoundedType(), c);
        return wildcard;
    }

    private void arguments(JContainer<Expression> arguments, Counter c) {
        visitSpace(arguments.getBefore(), Space.Location.METHOD_INVOCATION_ARGUMENTS, c);
        List<JRightPadded<Expression>> elements = arguments.getPadding().getElements();
        int last = elements.size() - 1;
        for (int i = 0; i <= last; i++) {
            JRightPadded<Expression> argument = elements.get(i);
            if (i == last && argument.getElement().getMarkers().findFirst(TrailingLambdaArgument.class).isPresent()) {
                visitSpace(argument.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
                visit(argument.getElement(), c);
            } else {
                rightPadded(argument, c);
            }
        }
    }

    private void parameters(JContainer<Statement> parameters, int from, Counter c) {
        beforeSyntax(parameters.getBefore(), parameters.getMarkers(), c);
        List<JRightPadded<Statement>> elements = parameters.getPadding().getElements();
        for (int i = from; i < elements.size(); i++) {
            JRightPadded<Statement> parameter = elements.get(i);
            if (!isImplicit(parameter.getElement())) {
                rightPadded(parameter, c);
            }
        }
    }

    private void container(@Nullable JContainer<? extends J> container, Counter c) {
        if (container != null) {
            beforeSyntax(container.getBefore(), container.getMarkers(), c);
            for (JRightPadded<? extends J> element : container.getPadding().getElements()) {
                rightPadded(element, c);
            }
        }
    }

    private void rightPadded(@Nullable JRightPadded<? extends J> rightPadded, Counter c) {
        if (rightPadded != null) {
            visit(rightPadded.getElement(), c);
            visitSpace(rightPadded.getAfter(), Space.Location.LANGUAGE_EXTENSION, c);
            visitMarkers(rightPadded.getMarkers(), c);
        }
    }

    private void typeReferencePrefix(Markers markers, Counter c) {
        markers.findFirst(TypeReferencePrefix.class)
                .ifPresent(prefix -> visitSpace(prefix.getPrefix(), Space.Location.LANGUAGE_EXTENSION, c));
    }

    private void beforeSyntax(J j, Counter c) {
        beforeSyntax(j.getPrefix(), j.getMarkers(), c);
    }

    private void beforeSyntax(Space prefix, Markers markers, Counter c) {
        visitSpace(prefix, Space.Location.LANGUAGE_EXTENSION, c);
        visitMarkers(markers, c);
    }

    private static boolean isImplicit(J j) {
        return j.getMarkers().findFirst(Implicit.class).isPresent();
    }
}
