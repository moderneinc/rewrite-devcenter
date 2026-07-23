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
import org.openrewrite.Cursor;
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
        // No other Comment type exists in practice; render defensively if one ever does.
        return countNewlines(comment.printComment(new Cursor(null, "root")));
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
     * line not terminated by a newline, or zero for a source file that prints to nothing.
     *
     * @param eof the source file's trailing space (last thing printed)
     */
    static long lineCount(Counter c, @Nullable Space eof, Cursor cursor) {
        String trailing = eof == null ? "" : trailingText(eof, cursor);
        boolean endsWithNewline = !trailing.isEmpty() && trailing.charAt(trailing.length() - 1) == '\n';
        if (endsWithNewline) {
            return c.newlines;
        }
        if (c.newlines == 0 && !c.sawText && trailing.isEmpty()) {
            return 0;
        }
        return c.newlines + 1;
    }

    /** The effective trailing text of a source file's final space: enough to know its last character. */
    private static String trailingText(Space eof, Cursor cursor) {
        List<Comment> comments = eof.getComments();
        if (!comments.isEmpty()) {
            Comment last = comments.get(comments.size() - 1);
            String suffix = last.getSuffix();
            return suffix.isEmpty() ? last.printComment(cursor) : suffix;
        }
        return eof.getWhitespace();
    }
}
