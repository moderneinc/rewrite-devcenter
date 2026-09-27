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
import org.openrewrite.Cursor;
import org.openrewrite.golang.GoSumVisitor;
import org.openrewrite.golang.tree.GoSum;
import org.openrewrite.golang.tree.GoSumTree;
import org.openrewrite.java.tree.Space;

/**
 * Counts lines in a go.sum LST in-process. Like Go source, go.sum prints out-of-process over RPC.
 */
final class GoSumLineCounter extends GoSumVisitor<Counter> {

    @Override
    public Space visitSpace(Space space, Counter count) {
        Newlines.addSpace(count, space);
        return space;
    }

    @Override
    public GoSumTree visitLine(GoSum.Line line, Counter count) {
        count.sawText = true;
        return super.visitLine(line, count);
    }

    static long count(GoSum goSum) {
        Counter c = new Counter();
        new GoSumLineCounter().visit(goSum, c);
        return Newlines.lineCount(c, goSum.getEof(), new Cursor(null, goSum));
    }
}
