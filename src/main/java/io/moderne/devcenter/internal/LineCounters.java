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

import org.openrewrite.SourceFile;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.golang.tree.Go;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.python.tree.Py;

/**
 * Counts lines of code in a source file without materializing its printed form.
 * <p>
 * Languages whose printer runs out-of-process over RPC (JavaScript/TypeScript, Python, C#, Go) are the
 * expensive ones to {@code printAll()}, so each gets a dedicated in-process visitor that walks the LST
 * directly. Everything else falls back to {@link LineCountingOutputCapture}, which drives the source file's
 * own (in-process) printer but discards the output, tallying newlines as they stream by. Both paths
 * reproduce the exact line count of {@link SourceFile#printAll()}.
 */
public final class LineCounters {

    private LineCounters() {
    }

    public static long count(SourceFile sourceFile) {
        if (sourceFile instanceof J.CompilationUnit) {
            return JavaLineCounter.count((J.CompilationUnit) sourceFile);
        }
        if (sourceFile instanceof JS.CompilationUnit) {
            return JavaScriptLineCounter.count((JS.CompilationUnit) sourceFile);
        }
        if (sourceFile instanceof Py.CompilationUnit) {
            return PythonLineCounter.count((Py.CompilationUnit) sourceFile);
        }
        if (sourceFile instanceof Cs.CompilationUnit) {
            return CSharpLineCounter.count((Cs.CompilationUnit) sourceFile);
        }
        if (sourceFile instanceof Go.CompilationUnit) {
            return GoLineCounter.count((Go.CompilationUnit) sourceFile);
        }
        return printingCount(sourceFile);
    }

    private static long printingCount(SourceFile sourceFile) {
        LineCountingOutputCapture capture = new LineCountingOutputCapture();
        sourceFile.printAll(capture);
        return capture.lineCount();
    }
}
