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
import org.openrewrite.golang.GoModVisitor;
import org.openrewrite.golang.tree.GoMod;
import org.openrewrite.golang.tree.GoModTree;
import org.openrewrite.java.tree.Space;

/**
 * Counts lines in a go.mod LST in-process. Like Go source, go.mod prints out-of-process over RPC.
 */
final class GoModLineCounter extends GoModVisitor<Counter> {

    @Override
    public Space visitSpace(Space space, Counter count) {
        Newlines.addSpace(count, space);
        return space;
    }

    @Override
    public GoModTree visitDirective(GoMod.Directive directive, Counter count) {
        Newlines.addText(count, directive.getKeyword());
        return super.visitDirective(directive, count);
    }

    @Override
    public GoModTree visitBlock(GoMod.Block block, Counter count) {
        count.sawText = true;
        return super.visitBlock(block, count);
    }

    @Override
    public GoModTree visitValue(GoMod.Value value, Counter count) {
        Newlines.addText(count, value.getText());
        return super.visitValue(value, count);
    }

    static long count(GoMod goMod) {
        Counter c = new Counter();
        new GoModLineCounter().visit(goMod, c);
        return Newlines.lineCount(c, goMod.getEof());
    }
}
