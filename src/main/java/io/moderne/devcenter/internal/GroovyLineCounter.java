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
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.groovy.marker.AsStyleTypeCast;
import org.openrewrite.groovy.marker.Elvis;
import org.openrewrite.groovy.marker.EmptyArgumentListPrecedesArgument;
import org.openrewrite.groovy.marker.LambdaStyle;
import org.openrewrite.groovy.marker.MultiVariable;
import org.openrewrite.groovy.marker.RedundantDef;
import org.openrewrite.groovy.marker.Semicolon;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Marker;

import java.util.List;

/**
 * Counts lines in a Groovy LST (including build.gradle scripts) from its source text, without printing it.
 * Mirrors GroovyPrinter where it prints text the visitor doesn't reach, or skips fields the visitor visits.
 * Text that markers would print isn't source and isn't counted.
 */
final class GroovyLineCounter extends GroovyVisitor<Counter> {

    @Override
    public J visitCompilationUnit(G.CompilationUnit cu, Counter count) {
        Newlines.addText(count, cu.getShebang());
        visitSpace(cu.getPrefix(), Space.Location.COMPILATION_UNIT_PREFIX, count);
        visitMarkers(cu.getMarkers(), count);
        JRightPadded<J.Package> pkg = cu.getPadding().getPackageDeclaration();
        if (pkg != null) {
            count.sawText = true;
            visitRightPadded(pkg, JRightPadded.Location.PACKAGE, count);
        }
        // GroovyVisitor skips the statements' padding, which GroovyPrinter prints
        for (JRightPadded<Statement> statement : cu.getPadding().getStatements()) {
            if (!(statement.getElement() instanceof J.Empty)) {
                count.sawText = true;
            }
            visitRightPadded(statement, JRightPadded.Location.LANGUAGE_EXTENSION, count);
        }
        visitSpace(cu.getEof(), Space.Location.COMPILATION_UNIT_EOF, count);
        return cu;
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, Counter count) {
        Newlines.addSpace(count, space);
        return space;
    }

    @Override
    public J visitLiteral(J.Literal literal, Counter count) {
        Newlines.addText(count, literal.getValueSource());
        return super.visitLiteral(literal, count);
    }

    @Override
    public J visitUnknownSource(J.Unknown.Source source, Counter count) {
        Newlines.addText(count, source.getText());
        return super.visitUnknownSource(source, count);
    }

    @Override
    public J visitErroneous(J.Erroneous erroneous, Counter count) {
        Newlines.addText(count, erroneous.getText());
        return super.visitErroneous(erroneous, count);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <M extends Marker> M visitMarker(Marker marker, Counter count) {
        // Source syntax the parser keeps in markers
        if (marker instanceof TrailingComma) {
            Newlines.addText(count, ",");
            Newlines.addSpace(count, ((TrailingComma) marker).getSuffix());
        } else if (marker instanceof org.openrewrite.groovy.TrailingComma) {
            Newlines.addText(count, ",");
            Newlines.addSpace(count, ((org.openrewrite.groovy.TrailingComma) marker).getSuffix());
        } else if (marker instanceof Semicolon || marker instanceof org.openrewrite.java.marker.Semicolon) {
            Newlines.addText(count, ";");
        } else if (marker instanceof RedundantDef) {
            Newlines.addSpace(count, ((RedundantDef) marker).getPrefix());
            Newlines.addText(count, "def");
        } else if (marker instanceof MultiVariable) {
            Newlines.addSpace(count, ((MultiVariable) marker).getPrefix());
            Newlines.addText(count, ",");
        } else if (marker instanceof EmptyArgumentListPrecedesArgument && printsEmptyArgumentList()) {
            Newlines.addSpace(count, ((EmptyArgumentListPrecedesArgument) marker).getPrefix());
            Newlines.addSpace(count, ((EmptyArgumentListPrecedesArgument) marker).getInfix());
        }
        return super.visitMarker(marker, count);
    }

    // GroovyPrinter ignores the marker once a recipe changes the arguments
    private boolean printsEmptyArgumentList() {
        Object value = getCursor().getValue();
        if (!(value instanceof J.MethodInvocation)) {
            return false;
        }
        List<Expression> arguments = ((J.MethodInvocation) value).getArguments();
        return arguments.size() == 1 && arguments.get(0) instanceof J.Lambda;
    }

    @Override
    public J visitBinary(G.Binary binary, Counter count) {
        if (binary.getOperator() == G.Binary.Type.Access) {
            visitSpace(binary.getAfter(), Space.Location.LANGUAGE_EXTENSION, count);
        }
        return super.visitBinary(binary, count);
    }

    @Override
    public J visitUnary(G.Unary unary, Counter count) {
        // GroovyPrinter prints the spread operator without its padding
        visitSpace(unary.getPrefix(), Space.Location.UNARY_PREFIX, count);
        visitMarkers(unary.getMarkers(), count);
        visit(unary.getExpression(), count);
        return unary;
    }

    @Override
    public J visitTernary(J.Ternary ternary, Counter count) {
        if (!ternary.getMarkers().findFirst(Elvis.class).isPresent()) {
            return super.visitTernary(ternary, count);
        }
        // The parser repeats the condition as the true part, which GroovyPrinter skips
        visitSpace(ternary.getPrefix(), Space.Location.TERNARY_PREFIX, count);
        visitMarkers(ternary.getMarkers(), count);
        visit(ternary.getCondition(), count);
        visitSpace(ternary.getPadding().getTruePart().getBefore(), Space.Location.TERNARY_TRUE, count);
        visitSpace(ternary.getPadding().getFalsePart().getBefore(), Space.Location.TERNARY_FALSE, count);
        visit(ternary.getFalsePart(), count);
        return ternary;
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast, Counter count) {
        if (!typeCast.getMarkers().findFirst(AsStyleTypeCast.class).isPresent()) {
            return super.visitTypeCast(typeCast, count);
        }
        visitSpace(typeCast.getPrefix(), Space.Location.TYPE_CAST_PREFIX, count);
        visitMarkers(typeCast.getMarkers(), count);
        visit(typeCast.getExpression(), count);
        JRightPadded<TypeTree> clazz = typeCast.getClazz().getPadding().getTree();
        visitSpace(clazz.getAfter(), Space.Location.CONTROL_PARENTHESES_PREFIX, count);
        visit(clazz.getElement(), count);
        return typeCast;
    }

    @Override
    public J visitLambda(J.Lambda lambda, Counter count) {
        LambdaStyle style = lambda.getMarkers().findFirst(LambdaStyle.class).orElse(null);
        boolean javaStyle = style != null && style.isJavaStyle();
        boolean arrow = style == null ? !lambda.getParameters().getParameters().isEmpty() : style.isArrow();
        visitSpace(lambda.getPrefix(), Space.Location.LAMBDA_PREFIX, count);
        visitMarkers(lambda.getMarkers(), count);
        visit(lambda.getParameters(), count);
        if (arrow) {
            visitSpace(lambda.getArrow(), Space.Location.LAMBDA_ARROW_PREFIX, count);
        }
        if (lambda.getBody() instanceof J.Block && !javaStyle) {
            // A closure's braces are the lambda's, so GroovyPrinter prints only the block's contents
            J.Block block = (J.Block) lambda.getBody();
            for (JRightPadded<Statement> statement : block.getPadding().getStatements()) {
                visitRightPadded(statement, JRightPadded.Location.BLOCK_STATEMENT, count);
            }
            visitSpace(block.getEnd(), Space.Location.BLOCK_END, count);
        } else {
            visit(lambda.getBody(), count);
        }
        return lambda;
    }

    static long count(G.CompilationUnit cu) {
        Counter c = new Counter();
        new GroovyLineCounter().visit(cu, c);
        return Newlines.lineCount(c, cu.getEof());
    }
}
