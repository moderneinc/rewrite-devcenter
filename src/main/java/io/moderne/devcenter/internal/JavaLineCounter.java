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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.marker.CompactSourceFile;
import org.openrewrite.java.marker.OmitBraces;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Marker;

import java.util.List;

/**
 * Counts lines in a Java LST in-process from its source text, without reconstructing the source string.
 * Text that markers would print, like search result descriptions, isn't source and isn't counted.
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

    @Override
    public J visitErroneous(J.Erroneous erroneous, Counter count) {
        Newlines.addText(count, erroneous.getText());
        return super.visitErroneous(erroneous, count);
    }

    @Override
    public <M extends Marker> M visitMarker(Marker marker, Counter count) {
        if (marker instanceof TrailingComma) {
            // Source syntax the parser keeps in a marker, printed as "," followed by its suffix
            Newlines.addText(count, ",");
            Newlines.addSpace(count, ((TrailingComma) marker).getSuffix());
        }
        return super.visitMarker(marker, count);
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, Counter count) {
        if (classDecl.getMarkers().findFirst(CompactSourceFile.class).isPresent()) {
            // JavaPrinter prints only the body, and the parser repeats the first member's modifiers in the header
            visitSpace(classDecl.getPrefix(), Space.Location.CLASS_DECLARATION_PREFIX, count);
            visitMarkers(classDecl.getMarkers(), count);
            visit(classDecl.getBody(), count);
            return classDecl;
        }
        return super.visitClassDeclaration(classDecl, count);
    }

    static long count(J.CompilationUnit cu) {
        Counter c = new Counter();
        new JavaLineCounter().visit(cu, c);
        return Newlines.lineCount(c, trailingSpace(cu));
    }

    // A body without braces ends in its end space, which is the last source text when the EOF is empty
    private static Space trailingSpace(J.CompilationUnit cu) {
        Space eof = cu.getEof();
        List<J.ClassDeclaration> classes = cu.getClasses();
        if (eof.getWhitespace().isEmpty() && eof.getComments().isEmpty() && !classes.isEmpty()) {
            J.Block body = classes.get(classes.size() - 1).getBody();
            if (body.getMarkers().findFirst(OmitBraces.class).isPresent()) {
                return body.getEnd();
            }
        }
        return eof;
    }
}
