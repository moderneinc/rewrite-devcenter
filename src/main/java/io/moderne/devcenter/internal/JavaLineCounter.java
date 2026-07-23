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

import io.moderne.devcenter.internal.Newlines.Counter;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

/**
 * Counts lines in a Java LST in-process, matching the newline count that {@link J.CompilationUnit#printAll()}
 * would produce, without reconstructing the source string.
 */
final class JavaLineCounter extends JavaVisitor<Counter> {

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

    static long count(J.CompilationUnit cu) {
        Counter c = new Counter();
        JavaLineCounter v = new JavaLineCounter();
        v.visit(cu, c);
        return Newlines.lineCount(c, cu.getEof(), new Cursor(null, cu));
    }
}
