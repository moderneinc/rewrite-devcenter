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
package io.moderne.devcenter.internal;

import org.jspecify.annotations.Nullable;
import org.openrewrite.csharp.CSharpVisitor;
import org.openrewrite.csharp.CsDocCommentVisitor;
import org.openrewrite.csharp.tree.CsDocComment;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.python.tree.PyComment;

import java.util.List;

/**
 * Newline-counting helpers shared by the in-process, language-specific line counters. These count
 * newlines exactly where a language's printer would emit them — whitespace, comments (and their
 * suffixes), and literal value sources — without reconstructing the printed source.
 */
final class Newlines {

    private Newlines() {
    }

    /** Mutable accumulator threaded through a counting visitor. */
    static final class Counter {
        long newlines;
        boolean sawText;
    }

    static int countNewlines(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0, len = s.length(); i < len; i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    static void addText(Counter c, @Nullable String text) {
        if (text != null && !text.isEmpty()) {
            c.sawText = true;
            c.newlines += countNewlines(text);
        }
    }

    static void addSpace(Counter c, Space space) {
        addText(c, space.getWhitespace());
        List<Comment> comments = space.getComments();
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            c.sawText = true;
            c.newlines += commentNewlines(comment);
            addText(c, comment.getSuffix());
        }
    }

    private static int commentNewlines(Comment comment) {
        if (comment instanceof TextComment) {
            // Printed as "/*" + text + "*/" or "//" + text; only the text can hold newlines.
            return countNewlines(((TextComment) comment).getText());
        }
        if (comment instanceof PyComment) {
            // Python "#" comments are single-line; any newline is in the suffix, counted separately.
            return countNewlines(((PyComment) comment).getText());
        }
        if (comment instanceof Javadoc) {
            // A Javadoc's newlines all live in LineBreak margins (and, defensively, Text); count them
            // by walking the tree instead of rendering it to a string.
            int[] n = {0};
            new JavadocNewlineCounter().visit((Javadoc) comment, n);
            return n[0];
        }
        if (comment instanceof CsDocComment) {
            // C# XML doc comments are structured like Javadoc: newlines live in LineBreak margins and text.
            int[] n = {0};
            new CsDocCommentNewlineCounter().visit((CsDocComment) comment, n);
            return n[0];
        }
        // The counted languages have no other comment types
        return 0;
    }

    private static final class JavadocNewlineCounter extends JavadocVisitor<int[]> {
        JavadocNewlineCounter() {
            super(new JavaVisitor<>());
        }

        @Override
        public Javadoc visitLineBreak(Javadoc.LineBreak lineBreak, int[] n) {
            n[0] += countNewlines(lineBreak.getMargin());
            return super.visitLineBreak(lineBreak, n);
        }

        @Override
        public Javadoc visitText(Javadoc.Text text, int[] n) {
            n[0] += countNewlines(text.getText());
            return super.visitText(text, n);
        }
    }

    private static final class CsDocCommentNewlineCounter extends CsDocCommentVisitor<int[]> {
        CsDocCommentNewlineCounter() {
            super(new CSharpVisitor<>());
        }

        @Override
        public CsDocComment visitLineBreak(CsDocComment.LineBreak lineBreak, int[] n) {
            n[0] += countNewlines(lineBreak.getMargin());
            return super.visitLineBreak(lineBreak, n);
        }

        @Override
        public CsDocComment visitXmlText(CsDocComment.XmlText text, int[] n) {
            n[0] += countNewlines(text.getText());
            return super.visitXmlText(text, n);
        }
    }

    /**
     * Apply {@code FindOrganizationStatistics}' line-count formula: newline count plus one for a final
     * line not terminated by a newline, or zero for a source file with no text.
     *
     * @param eof the source file's trailing space (its last source text)
     */
    static long lineCount(Counter c, @Nullable Space eof) {
        if (eof != null && endsWithNewline(eof)) {
            return c.newlines;
        }
        if (c.newlines == 0 && !c.sawText && (eof == null || eof.getWhitespace().isEmpty() && eof.getComments().isEmpty())) {
            return 0;
        }
        return c.newlines + 1;
    }

    /** The same formula, for counters that track whether their source text ends with a newline. */
    static long lineCount(Counter c, boolean endsWithNewline) {
        if (endsWithNewline) {
            return c.newlines;
        }
        return c.newlines == 0 && !c.sawText ? 0 : c.newlines + 1;
    }

    /** The same formula, applied to a whole source text. */
    static long lineCount(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        return countNewlines(text) + (text.charAt(text.length() - 1) == '\n' ? 0 : 1);
    }

    static boolean endsWithNewline(Space space) {
        List<Comment> comments = space.getComments();
        if (comments.isEmpty()) {
            return space.getWhitespace().endsWith("\n");
        }
        Comment last = comments.get(comments.size() - 1);
        if (!last.getSuffix().isEmpty()) {
            return last.getSuffix().endsWith("\n");
        }
        // Parsers leave the newline after a comment in its suffix, though a C# doc comment's body may end with one
        if (last instanceof TextComment) {
            return !last.isMultiline() && ((TextComment) last).getText().endsWith("\n");
        }
        if (last instanceof PyComment) {
            return ((PyComment) last).getText().endsWith("\n");
        }
        if (last instanceof CsDocComment.DocComment) {
            List<CsDocComment> body = ((CsDocComment.DocComment) last).getBody();
            CsDocComment tail = body.isEmpty() ? null : body.get(body.size() - 1);
            return tail instanceof CsDocComment.LineBreak && ((CsDocComment.LineBreak) tail).getMargin().endsWith("\n") ||
                   tail instanceof CsDocComment.XmlText && ((CsDocComment.XmlText) tail).getText().endsWith("\n");
        }
        // Block Javadoc ends with "*/", and markdown Javadoc with its last line's text
        return false;
    }
}
