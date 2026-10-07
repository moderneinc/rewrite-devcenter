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
import org.openrewrite.java.marker.Semicolon;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Marker;
import org.openrewrite.ruby.RubyVisitor;
import org.openrewrite.ruby.tree.Rb;
import org.openrewrite.ruby.tree.RubyContainer;
import org.openrewrite.ruby.tree.RubyTextComment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static io.moderne.devcenter.internal.Newlines.countNewlines;

/**
 * Counts lines in a Ruby LST in-process from its source text, without reconstructing the source string.
 * Where the Ruby printer prints only part of a node, the node is walked the same way. Heredoc bodies print
 * after the next newline, which only matters for how the text ends.
 */
final class RubyLineCounter extends RubyVisitor<Counter> {
    private final Deque<Rb.Heredoc> openHeredocs = new ArrayDeque<>();
    private boolean endsWithNewline;

    static long count(Rb.CompilationUnit cu) {
        Counter c = new Counter();
        RubyLineCounter counter = new RubyLineCounter();
        counter.visit(cu, c);
        return Newlines.lineCount(c, counter.endsWithNewline);
    }

    @Override
    public @Nullable J preVisit(J tree, Counter c) {
        // Every node but these prints some syntax of its own
        if (!(tree instanceof J.Empty) && !(tree instanceof Rb.CompilationUnit)) {
            c.sawText = true;
        }
        return tree;
    }

    @Override
    public Rb visitCompilationUnit(Rb.CompilationUnit cu, Counter c) {
        Boolean tail = printSpace(cu.getPrefix(), c);
        visitMarkers(cu.getMarkers(), c);
        for (JRightPadded<Statement> statement : cu.getPadding().getStatements()) {
            visit(statement.getElement(), c);
            if (!(statement.getElement() instanceof J.Empty)) {
                tail = false;
            }
            Boolean after = printSpace(statement.getAfter(), c);
            if (after != null) {
                tail = after;
            }
            if (statement.getMarkers().findFirst(Semicolon.class).isPresent()) {
                tail = false;
            }
            visitMarkers(statement.getMarkers(), c);
        }
        Boolean eof = printSpace(cu.getEof(), c);
        if (eof != null) {
            tail = eof;
        }
        tail = printUnflushedHeredocs(tail, c);
        Rb.DataSection data = cu.getDataSection();
        if (data != null) {
            Boolean prefix = printSpace(data.getPrefix(), c);
            if (prefix != null) {
                tail = prefix;
            }
            visitMarkers(data.getMarkers(), c);
            if (!data.getText().isEmpty()) {
                Newlines.addText(c, data.getText());
                tail = data.getText().charAt(data.getText().length() - 1) == '\n';
            }
        }
        endsWithNewline = tail != null && tail;
        return cu;
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, Counter c) {
        printSpace(space, c);
        return space;
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
    public <M extends Marker> M visitMarker(Marker marker, Counter c) {
        if (marker instanceof TrailingComma) {
            trailingComma((TrailingComma) marker, c);
        }
        return super.visitMarker(marker, c);
    }

    @Override
    public <J2 extends J> @Nullable JContainer<J2> visitContainer(@Nullable JContainer<J2> container, JContainer.Location loc, Counter c) {
        JContainer<J2> visited = super.visitContainer(container, loc, c);
        containerTrailingComma(container, c);
        return visited;
    }

    // RubyVisitor sends Ruby containers straight to JavaVisitor's implementation
    @Override
    public <J2 extends J> @Nullable JContainer<J2> visitContainer(@Nullable JContainer<J2> container, RubyContainer.Location loc, Counter c) {
        return visitContainer(container, JContainer.Location.LANGUAGE_EXTENSION, c);
    }

    @Override
    public J visitHeredoc(Rb.Heredoc heredoc, Counter c) {
        printSpace(heredoc.getPrefix(), c);
        visitMarkers(heredoc.getMarkers(), c);
        c.sawText = true;
        String body = body(heredoc);
        c.newlines += countNewlines(body) + (endsWithBlankLine(body) ? 0 : 1) + countNewlines(heredoc.getDelimiter());
        c.newlines += newlines(heredoc.getEnd());
        openHeredocs.add(heredoc);
        return heredoc;
    }

    @Override
    public J visitComplexString(Rb.ComplexString complexString, Counter c) {
        printSpace(complexString.getPrefix(), c);
        visitMarkers(complexString.getMarkers(), c);
        visitContainer(complexString.getPadding().getStrings(), JContainer.Location.LANGUAGE_EXTENSION, c);
        return complexString;
    }

    @Override
    public J visitComplexStringValue(Rb.ComplexString.Value value, Counter c) {
        printSpace(value.getPrefix(), c);
        return super.visitComplexStringValue(value, c);
    }

    @Override
    public J visitClassMethod(Rb.ClassMethod classMethod, Counter c) {
        printSpace(classMethod.getPrefix(), c);
        visitMarkers(classMethod.getMarkers(), c);
        visit(classMethod.getReceiver(), c);
        JLeftPadded<J.MethodDeclaration> method = classMethod.getPadding().getMethod();
        printSpace(method.getBefore(), c);
        visitMarkers(method.getMarkers(), c);
        visit(method.getElement().getName(), c);
        visitContainer(method.getElement().getPadding().getParameters(), JContainer.Location.METHOD_DECLARATION_PARAMETERS, c);
        visit(method.getElement().getBody(), c);
        return classMethod;
    }

    @Override
    public J visitRescue(Rb.Rescue rescue, Counter c) {
        printSpace(rescue.getPrefix(), c);
        visitMarkers(rescue.getMarkers(), c);
        J.Try aTry = rescue.getTry();
        printSpace(aTry.getPrefix(), c);
        visitMarkers(aTry.getMarkers(), c);
        visit(aTry.getBody(), c);
        for (J.Try.Catch aCatch : aTry.getCatches()) {
            printSpace(aCatch.getPrefix(), c);
            visitMarkers(aCatch.getMarkers(), c);
            J.VariableDeclarations param = aCatch.getParameter().getTree();
            printSpace(param.getPrefix(), c);
            visitMarkers(param.getMarkers(), c);
            TypeTree types = param.getTypeExpression();
            if (types instanceof J.MultiCatch) {
                for (JRightPadded<NameTree> alternative : ((J.MultiCatch) types).getPadding().getAlternatives()) {
                    visitRightPadded(alternative, JRightPadded.Location.CATCH_ALTERNATIVE, c);
                }
            } else {
                visit(types, c);
            }
            for (J.VariableDeclarations.NamedVariable exceptionName : param.getVariables()) {
                printSpace(exceptionName.getPrefix(), c);
                visitMarkers(exceptionName.getMarkers(), c);
                visit(exceptionName.getName(), c);
            }
            visit(aCatch.getBody(), c);
        }
        if (rescue.getElse() != null) {
            printSpace(rescue.getElse().getPrefix(), c);
            visit(rescue.getElse().withPrefix(Space.EMPTY), c);
        }
        if (aTry.getFinally() != null) {
            printSpace(aTry.getFinally().getPrefix(), c);
            visit(aTry.getFinally().withPrefix(Space.EMPTY), c);
        }
        return rescue;
    }

    @Override
    public J visitCase(J.Case aCase, Counter c) {
        printSpace(aCase.getPrefix(), c);
        visitMarkers(aCase.getMarkers(), c);
        if (!aCase.getCaseLabels().isEmpty()) {
            visitContainer(aCase.getPadding().getCaseLabels(), JContainer.Location.CASE_LABEL, c);
        }
        visitContainer(aCase.getPadding().getStatements(), JContainer.Location.CASE, c);
        return aCase;
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, Counter c) {
        printSpace(classDecl.getPrefix(), c);
        visitMarkers(classDecl.getMarkers(), c);
        visit(classDecl.getName(), c);
        visitLeftPadded(classDecl.getPadding().getExtends(), JLeftPadded.Location.EXTENDS, c);
        visit(classDecl.getBody(), c);
        return classDecl;
    }

    @Override
    public J visitLambda(J.Lambda lambda, Counter c) {
        printSpace(lambda.getPrefix(), c);
        visitMarkers(lambda.getMarkers(), c);
        J.Lambda.Parameters parameters = lambda.getParameters();
        if (!parameters.getParameters().isEmpty()) {
            printSpace(parameters.getPrefix(), c);
            visitMarkers(parameters.getMarkers(), c);
            for (JRightPadded<J> parameter : parameters.getPadding().getParameters()) {
                visitRightPadded(parameter, JRightPadded.Location.LAMBDA_PARAM, c);
            }
        }
        if (lambda.getBody() instanceof J.Block) {
            J.Block body = (J.Block) lambda.getBody();
            printSpace(body.getPrefix(), c);
            for (JRightPadded<Statement> statement : body.getPadding().getStatements()) {
                visitRightPadded(statement, JRightPadded.Location.BLOCK_STATEMENT, c);
            }
            printSpace(body.getEnd(), c);
        } else {
            visit(lambda.getBody(), c);
        }
        return lambda;
    }

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, Counter c) {
        printSpace(method.getPrefix(), c);
        visitMarkers(method.getMarkers(), c);
        for (J.Annotation annotation : method.getAnnotations().getName().getAnnotations()) {
            visit(annotation, c);
        }
        visit(method.getName(), c);
        visitContainer(method.getPadding().getParameters(), JContainer.Location.METHOD_DECLARATION_PARAMETERS, c);
        visit(method.getBody(), c);
        return method;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, Counter c) {
        printSpace(method.getPrefix(), c);
        visitMarkers(method.getMarkers(), c);
        visitRightPadded(method.getPadding().getSelect(), JRightPadded.Location.METHOD_SELECT, c);
        visit(method.getName(), c);
        visitArgumentsThenBlock(method.getPadding().getArguments(), c);
        return method;
    }

    @Override
    public J visitNewClass(J.NewClass newClass, Counter c) {
        printSpace(newClass.getPrefix(), c);
        visitMarkers(newClass.getMarkers(), c);
        visit(newClass.getClazz(), c);
        JRightPadded<Expression> enclosing = newClass.getPadding().getEnclosing();
        if (enclosing != null) {
            printSpace(enclosing.getAfter(), c);
        }
        printSpace(newClass.getNew(), c);
        visitArgumentsThenBlock(newClass.getPadding().getArguments(), c);
        return newClass;
    }

    @Override
    public J visitSwitch(J.Switch aSwitch, Counter c) {
        printSpace(aSwitch.getPrefix(), c);
        visitMarkers(aSwitch.getMarkers(), c);
        visit(aSwitch.getSelector(), c);
        for (JRightPadded<Statement> statement : aSwitch.getCases().getPadding().getStatements()) {
            visitRightPadded(statement, JRightPadded.Location.BLOCK_STATEMENT, c);
        }
        printSpace(aSwitch.getCases().getEnd(), c);
        return aSwitch;
    }

    @Override
    public J visitUnary(J.Unary unary, Counter c) {
        printSpace(unary.getPrefix(), c);
        visitMarkers(unary.getMarkers(), c);
        visit(unary.getExpression(), c);
        if (unary.getOperator() == J.Unary.Type.PostIncrement || unary.getOperator() == J.Unary.Type.PostDecrement) {
            printSpace(unary.getPadding().getOperator().getBefore(), c);
        }
        return unary;
    }

    // The printer lifts a do/end or brace block out of the arguments and prints it after them, without its padding
    private void visitArgumentsThenBlock(JContainer<Expression> args, Counter c) {
        printSpace(args.getBefore(), c);
        Rb.Block block = null;
        for (JRightPadded<Expression> arg : args.getPadding().getElements()) {
            if (arg.getElement() instanceof Rb.Block) {
                block = (Rb.Block) arg.getElement();
            } else {
                visitRightPadded(arg, JRightPadded.Location.METHOD_INVOCATION_ARGUMENT, c);
            }
        }
        containerTrailingComma(args, c);
        visit(block, c);
    }

    // Printed after the elements, where Ruby writes it
    private void containerTrailingComma(@Nullable JContainer<?> container, Counter c) {
        if (container != null) {
            for (Marker marker : container.getMarkers().getMarkers()) {
                if (marker instanceof TrailingComma) {
                    trailingComma((TrailingComma) marker, c);
                }
            }
        }
    }

    private void trailingComma(TrailingComma comma, Counter c) {
        c.sawText = true;
        printSpace(comma.getSuffix(), c);
    }

    /**
     * Counts a printed space, flushing the heredocs waiting for a newline as the printer does.
     *
     * @return whether the text printed so far ends with a newline, or null if the space prints nothing
     */
    private @Nullable Boolean printSpace(Space space, Counter c) {
        if (space.getWhitespace().isEmpty() && space.getComments().isEmpty()) {
            return null;
        }
        c.sawText = true;
        int n = newlines(space);
        c.newlines += n;
        boolean endsWithNewline = endsWithNewline(space);
        if (n == 0 || openHeredocs.isEmpty()) {
            return endsWithNewline;
        }
        return flush(n, endsWithNewline);
    }

    /**
     * Each heredoc body follows the next newline of the flushing space; what the space has left after them
     * prints last.
     */
    private boolean flush(int spaceNewlines, boolean spaceEndsWithNewline) {
        int flushed = openHeredocs.size();
        Rb.Heredoc last = openHeredocs.getLast();
        openHeredocs.clear();
        if (spaceNewlines > flushed) {
            return spaceEndsWithNewline;
        }
        return spaceEndsWithNewline && heredocEndsWithNewline(last);
    }

    // A heredoc no space flushed prints after the end of file, each after a newline of its own
    private @Nullable Boolean printUnflushedHeredocs(@Nullable Boolean tail, Counter c) {
        while (!openHeredocs.isEmpty()) {
            Rb.Heredoc heredoc = openHeredocs.poll();
            c.newlines++;
            Space end = heredoc.getEnd();
            int n = newlines(end);
            tail = n > 0 && !openHeredocs.isEmpty() ?
                    flush(n, endsWithNewline(end)) :
                    heredocEndsWithNewline(heredoc);
        }
        return tail;
    }

    private static boolean heredocEndsWithNewline(Rb.Heredoc heredoc) {
        Space end = heredoc.getEnd();
        if (!end.getWhitespace().isEmpty() || !end.getComments().isEmpty()) {
            return endsWithNewline(end);
        }
        if (!heredoc.getDelimiter().isEmpty()) {
            return false;
        }
        String body = body(heredoc);
        return !endsWithBlankLine(body) || body.endsWith("\n");
    }

    private static String body(Rb.Heredoc heredoc) {
        J.Literal value = heredoc.getValue();
        String body = value.getValueSource();
        if (body == null) {
            body = value.getValue() == null ? "" : value.getValue().toString();
        }
        return body;
    }

    // The printer adds a newline before the terminator unless the body already ends in a blank line
    private static boolean endsWithBlankLine(String body) {
        for (int i = body.length() - 1; i >= 0; i--) {
            char ch = body.charAt(i);
            if (ch == '\n') {
                return true;
            } else if (ch != ' ' && ch != '\t' && ch != '\r') {
                return false;
            }
        }
        return true;
    }

    private static int newlines(Space space) {
        int n = countNewlines(space.getWhitespace());
        List<Comment> comments = space.getComments();
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            n += countNewlines(text(comment)) + countNewlines(comment.getSuffix());
        }
        return n;
    }

    // Printed as "#" + text or "=begin" + text + "=end"
    private static @Nullable String text(Comment comment) {
        if (comment instanceof RubyTextComment) {
            return ((RubyTextComment) comment).getText();
        }
        return comment instanceof TextComment ? ((TextComment) comment).getText() : null;
    }

    private static boolean endsWithNewline(Space space) {
        List<Comment> comments = space.getComments();
        if (!comments.isEmpty()) {
            Comment last = comments.get(comments.size() - 1);
            if (last.getSuffix().isEmpty() && last instanceof RubyTextComment) {
                return !last.isMultiline() && ((RubyTextComment) last).getText().endsWith("\n");
            }
        }
        return Newlines.endsWithNewline(space);
    }
}
